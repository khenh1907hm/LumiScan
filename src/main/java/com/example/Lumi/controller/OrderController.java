package com.example.Lumi.controller;

import com.example.Lumi.model.Order;
import com.example.Lumi.model.OrderItem;
import com.example.Lumi.repository.OrderRepository;
import com.example.Lumi.repository.OrderItemRepository;
import com.example.Lumi.service.CategoryService;
import com.example.Lumi.service.MenuItemService;
import com.example.Lumi.service.OrderService;
import com.example.Lumi.service.TableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/order")
public class OrderController {

    private final TableService tableService;
    private final CategoryService categoryService;
    private final MenuItemService menuItemService;
    private final OrderService orderService;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Autowired
    public OrderController(TableService tableService,
                           CategoryService categoryService,
                           MenuItemService menuItemService,
                           OrderService orderService,
                           SimpMessagingTemplate messagingTemplate,
                           OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository) {
        this.tableService = tableService;
        this.categoryService = categoryService;
        this.menuItemService = menuItemService;
        this.orderService = orderService;
        this.messagingTemplate = messagingTemplate;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    // GET: Hiển thị menu cho bàn (cho phép khách hàng truy cập công khai)
    @GetMapping("/{tableNumber}")
    @PreAuthorize("permitAll()")
    public String showMenu(@PathVariable String tableNumber, Model model) {
        System.out.println("=== ORDER CONTROLLER: showMenu called ===");
        System.out.println("Table Number: " + tableNumber);
        System.out.println("Request URL: /order/" + tableNumber);
        
        try {
            // Kiểm tra bàn tồn tại
            var table = tableService.findByTableNumber(tableNumber);
            if (table.isEmpty()) {
                System.out.println("ERROR: Table not found: " + tableNumber);
                model.addAttribute("error", "Bàn không tồn tại");
                return "error";
            }

            System.out.println("Table found: " + table.get().getTableNumber() + ", Status: " + table.get().getStatus());

            // Kiểm tra xem bàn có order hiện tại không
            Optional<Order> currentOrderOpt = orderService.getCurrentOrderByTable(tableNumber);
            
            // Nếu bàn đang occupied nhưng có order hiện tại, vẫn cho phép vào để xem và thêm món
            // Chỉ redirect về table-in-use nếu bàn occupied nhưng không có order (trường hợp lỗi)
            if (!"available".equalsIgnoreCase(table.get().getStatus()) && currentOrderOpt.isEmpty()) {
                System.out.println("WARNING: Table status is not available and no order found: " + table.get().getStatus());
                model.addAttribute("tableNumber", tableNumber);
                model.addAttribute("tableStatus", table.get().getStatus());
                return "customer/table-in-use";
            }
            if (currentOrderOpt.isPresent()) {
                Order currentOrder = currentOrderOpt.get();
                model.addAttribute("currentOrder", currentOrder);
                model.addAttribute("orderId", currentOrder.getId());
                model.addAttribute("orderStatus", currentOrder.getStatus().toString());
                System.out.println("Found existing order: ID=" + currentOrder.getId() + ", Status=" + currentOrder.getStatus());
            } else {
                model.addAttribute("orderId", null);
                model.addAttribute("orderStatus", null);
                System.out.println("No existing order found for table " + tableNumber);
            }

            // Đưa dữ liệu ra giao diện
            model.addAttribute("tableNumber", tableNumber);
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("menuItems", menuItemService.getAllMenuItems());

            System.out.println("SUCCESS: Returning customer/order template");
            return "customer/order";

        } catch (Exception e) {
            System.out.println("EXCEPTION in showMenu: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "error";
        }
    }

    // POST: Nhận JSON từ frontend để đặt order (cho phép khách hàng công khai)
    @PostMapping("/submit")
    @PreAuthorize("permitAll()")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitOrder(@RequestBody OrderSubmitRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Validate input
            if (request.getTableNumber() == null || request.getTableNumber().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Số bàn không được để trống");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (request.getItems() == null || request.getItems().isEmpty()) {
                response.put("success", false);
                response.put("message", "Vui lòng chọn ít nhất một món");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validate và filter items có quantity > 0
            Map<Long, Integer> validItems = new HashMap<>();
            for (Map.Entry<Long, Integer> entry : request.getItems().entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    validItems.put(entry.getKey(), entry.getValue());
                }
            }
            
            if (validItems.isEmpty()) {
                response.put("success", false);
                response.put("message", "Vui lòng chọn ít nhất một món với số lượng > 0");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Chuyển đổi items từ Map<Long, Integer> thành List<OrderItemRequest>
            List<OrderService.OrderItemRequest> orderItems = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : validItems.entrySet()) {
                OrderService.OrderItemRequest itemReq = new OrderService.OrderItemRequest();
                itemReq.setMenuItemId(entry.getKey());
                itemReq.setQuantity(entry.getValue());
                orderItems.add(itemReq);
            }
            
            // Xử lý race condition: Kiểm tra lại order hiện tại trong transaction
            // Sử dụng synchronized trên tableNumber để tránh race condition
            String tableNumber = request.getTableNumber().trim();
            synchronized (("table_" + tableNumber).intern()) {
                // Kiểm tra lại order hiện tại (có thể đã được tạo bởi request khác)
                Optional<Order> currentOrderOpt = orderService.getCurrentOrderByTable(tableNumber);
                Order order;
                
                if (currentOrderOpt.isPresent()) {
                    // Nếu có order hiện tại, thêm món vào order đó
                    System.out.println("=== Adding items to existing order: " + currentOrderOpt.get().getId() + " ===");
                    order = orderService.addItemsToOrder(currentOrderOpt.get().getId(), orderItems);
                    response.put("message", "Thêm món thành công! Mã đơn: " + order.getId());
                } else {
                    // Nếu chưa có, tạo order mới
                    System.out.println("=== Creating new order for table: " + tableNumber + " ===");
                    System.out.println("Order items count: " + orderItems.size());
                    order = orderService.createOrder(tableNumber, orderItems);
                    System.out.println("Order created successfully! Order ID: " + order.getId());
                    response.put("message", "Đặt món thành công! Mã đơn: " + order.getId());
                }
                
                System.out.println("Order status: " + order.getStatus());
                System.out.println("Order items count: " + (order.getItems() != null ? order.getItems().size() : 0));

                // Gửi thông báo realtime đến employee
                messagingTemplate.convertAndSend("/topic/orders",
                        "Order updated from table " + tableNumber + " - Order ID: " + order.getId());

                response.put("success", true);
                response.put("orderId", order.getId());
                response.put("orderStatus", order.getStatus().toString());
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            System.out.println("Error in submitOrder: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // GET: Lấy order theo ID (cho admin/employee) - JSON API
    @GetMapping("/by-id/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOrderByIdJson(@PathVariable Long orderId) {
        Map<String, Object> response = new HashMap<>();
        try {
            var orderOpt = orderService.getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Order không tồn tại");
                return ResponseEntity.badRequest().body(response);
            }
            
            Order order = orderOpt.get();
            
            // Tạo DTO
            Map<String, Object> orderDTO = new HashMap<>();
            orderDTO.put("id", order.getId());
            orderDTO.put("status", order.getStatus().toString());
            orderDTO.put("createdAt", order.getCreatedAt().toString());
            orderDTO.put("tableNumber", order.getTable().getTableNumber());
            
            List<Map<String, Object>> itemsDTO = new ArrayList<>();
            if (order.getItems() != null) {
                for (var item : order.getItems()) {
                    Map<String, Object> itemDTO = new HashMap<>();
                    itemDTO.put("id", item.getId());
                    itemDTO.put("quantity", item.getQuantity());
                    itemDTO.put("price", item.getPrice());
                    
                    Map<String, Object> menuItemDTO = new HashMap<>();
                    menuItemDTO.put("id", item.getMenuItem().getId());
                    menuItemDTO.put("name", item.getMenuItem().getName());
                    menuItemDTO.put("price", item.getMenuItem().getPrice());
                    itemDTO.put("menuItem", menuItemDTO);
                    
                    itemsDTO.add(itemDTO);
                }
            }
            orderDTO.put("items", itemsDTO);
            
            response.put("success", true);
            response.put("order", orderDTO);
            response.put("total", orderService.calculateTotal(order));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // GET: Lấy order theo bàn (cho employee)
    @GetMapping("/table/{tableNumber}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOrderByTable(@PathVariable String tableNumber) {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println("=== getOrderByTable called for table: " + tableNumber + " ===");
            
            var tableOpt = tableService.findByTableNumber(tableNumber);
            if (tableOpt.isEmpty()) {
                System.out.println("ERROR: Table not found: " + tableNumber);
                response.put("success", false);
                response.put("message", "Bàn không tồn tại");
                return ResponseEntity.badRequest().body(response);
            }
            var table = tableOpt.get();
            System.out.println("Table found: ID=" + table.getId() + ", Status=" + table.getStatus());
            
            // Lấy tất cả order của bàn này (không filter theo status hoặc date)
            List<Order> allOrders = orderRepository.findAll();
            System.out.println("Total orders in DB: " + allOrders.size());
            
            // Filter orders của bàn này, chưa thanh toán
            List<Order> orders = allOrders.stream()
                .filter(o -> {
                    if (o.getTable() == null) return false;
                    boolean matches = o.getTable().getId().equals(table.getId());
                    if (matches) {
                        System.out.println("Found order: ID=" + o.getId() + ", Status=" + o.getStatus() + ", CreatedAt=" + o.getCreatedAt());
                    }
                    return matches;
                })
                .filter(o -> o.getStatus() != Order.Status.paid && o.getStatus() != Order.Status.done)
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // Mới nhất trước
                .toList();
            
            System.out.println("Filtered orders for table " + tableNumber + ": " + orders.size());
            
            if (orders.isEmpty()) {
                System.out.println("WARNING: No active orders found for table " + tableNumber);
                response.put("success", false);
                response.put("message", "Không có order nào cho bàn này");
                return ResponseEntity.ok(response);
            }
            
            Order order = orders.get(0); // Lấy order mới nhất
            System.out.println("Returning order: ID=" + order.getId() + ", Status=" + order.getStatus() + ", Items=" + (order.getItems() != null ? order.getItems().size() : 0));
            
            // Tạo DTO để tránh circular reference
            Map<String, Object> orderDTO = new HashMap<>();
            orderDTO.put("id", order.getId());
            orderDTO.put("status", order.getStatus().toString());
            orderDTO.put("createdAt", order.getCreatedAt().toString());
            orderDTO.put("tableNumber", order.getTable().getTableNumber());
            
            // Convert items to DTO
            List<Map<String, Object>> itemsDTO = new ArrayList<>();
            if (order.getItems() != null) {
                for (var item : order.getItems()) {
                    Map<String, Object> itemDTO = new HashMap<>();
                    itemDTO.put("id", item.getId());
                    itemDTO.put("quantity", item.getQuantity());
                    itemDTO.put("price", item.getPrice());
                    
                    Map<String, Object> menuItemDTO = new HashMap<>();
                    menuItemDTO.put("id", item.getMenuItem().getId());
                    menuItemDTO.put("name", item.getMenuItem().getName());
                    menuItemDTO.put("price", item.getMenuItem().getPrice());
                    itemDTO.put("menuItem", menuItemDTO);
                    
                    itemsDTO.add(itemDTO);
                }
            }
            orderDTO.put("items", itemsDTO);
            
            response.put("success", true);
            response.put("order", orderDTO);
            response.put("total", orderService.calculateTotal(order));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // POST: Cập nhật items trong order (cho employee và khách hàng)
    @PostMapping("/update-items")
    @PreAuthorize("permitAll()")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateOrderItems(@RequestBody UpdateOrderRequest request, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println("=== updateOrderItems called ===");
            System.out.println("Request orderId: " + request.getOrderId());
            System.out.println("Request items count: " + (request.getItems() != null ? request.getItems().size() : "null"));
            
            // Xác định quyền của user
            boolean isEmployee = false;
            boolean isAdmin = false;
            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
                isEmployee = authorities.contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
                isAdmin = authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            
            if (request.getOrderId() == null) {
                response.put("success", false);
                response.put("message", "Order ID không được để trống");
                return ResponseEntity.badRequest().body(response);
            }
            
            var orderOpt = orderService.getOrderById(request.getOrderId());
            if (orderOpt.isEmpty()) {
                System.out.println("Order not found: " + request.getOrderId());
                response.put("success", false);
                response.put("message", "Order không tồn tại");
                return ResponseEntity.badRequest().body(response);
            }
            Order order = orderOpt.get();
            
            System.out.println("Order found: ID=" + order.getId() + ", Status=" + order.getStatus() + ", Table=" + order.getTable().getTableNumber());
            
            // Nếu items rỗng, gọi method xóa hết items
            if (request.getItems() == null || request.getItems().isEmpty()) {
                System.out.println("Clearing all items from order " + order.getId());
                // Chỉ customer mới được xóa hết khi pending, employee/admin có thể xóa ở mọi trạng thái (trừ paid/done)
                if (!isEmployee && !isAdmin && order.getStatus() != Order.Status.pending) {
                    response.put("success", false);
                    response.put("message", "Không thể xóa món trong đơn hàng đã được xử lý");
                    return ResponseEntity.badRequest().body(response);
                }
                try {
                    order = orderService.clearOrderItems(request.getOrderId(), isEmployee || isAdmin);
                    messagingTemplate.convertAndSend("/topic/orders", "Order cleared for table " + order.getTable().getTableNumber());
                    response.put("success", true);
                    response.put("message", "Xóa giỏ hàng thành công");
                    return ResponseEntity.ok(response);
                } catch (IllegalArgumentException e) {
                    response.put("success", false);
                    response.put("message", e.getMessage());
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            // Kiểm tra quyền cập nhật: Customer chỉ được cập nhật khi pending, Employee/Admin được cập nhật ở mọi trạng thái (trừ paid/done)
            if (!isEmployee && !isAdmin) {
                // Customer chỉ được cập nhật khi pending
                if (order.getStatus() != Order.Status.pending) {
                    System.out.println("Order status is not pending: " + order.getStatus());
                    response.put("success", false);
                    response.put("message", "Không thể cập nhật đơn hàng đã được xử lý");
                    return ResponseEntity.badRequest().body(response);
                }
            } else {
                // Employee/Admin không được cập nhật order đã thanh toán
                if (order.getStatus() == Order.Status.paid || order.getStatus() == Order.Status.done) {
                    response.put("success", false);
                    response.put("message", "Không thể cập nhật đơn hàng đã thanh toán");
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            // Lấy danh sách items hiện tại để giữ giá cũ
            Map<Long, BigDecimal> existingPrices = new HashMap<>();
            if (order.getItems() != null) {
                for (OrderItem existingItem : order.getItems()) {
                    existingPrices.put(existingItem.getMenuItem().getId(), existingItem.getPrice());
                }
            }
            
            // Validate và filter items: bỏ qua items có quantity <= 0
            List<OrderService.OrderItemRequest> validItems = new ArrayList<>();
            for (OrderService.OrderItemRequest itemReq : request.getItems()) {
                if (itemReq.getQuantity() <= 0) {
                    System.out.println("Skipping item with quantity <= 0: menuItemId=" + itemReq.getMenuItemId());
                    continue; // Bỏ qua items có quantity <= 0
                }
                validItems.add(itemReq);
            }
            
            // Nếu sau khi filter không còn item nào hợp lệ, xóa hết items
            if (validItems.isEmpty()) {
                System.out.println("No valid items after filtering, clearing all items");
                order = orderService.clearOrderItems(request.getOrderId(), isEmployee || isAdmin);
                messagingTemplate.convertAndSend("/topic/orders", "Order cleared for table " + order.getTable().getTableNumber());
                response.put("success", true);
                response.put("message", "Xóa giỏ hàng thành công");
                return ResponseEntity.ok(response);
            }
            
            // Xóa toàn bộ items cũ và thêm lại danh sách mới
            orderItemRepository.deleteByOrder(order); // Xóa tất cả items cũ trong DB
            if (order.getItems() != null) {
                order.getItems().clear(); // Đồng bộ collection trong bộ nhớ
            }
            
            // Thêm lại items với giá phù hợp: giữ giá cũ nếu có, dùng giá mới nếu chưa có
            for (OrderService.OrderItemRequest itemReq : validItems) {
                var menuItem = menuItemService.getMenuItemByIdOrThrow(itemReq.getMenuItemId());
                
                // Giữ giá cũ nếu item đã tồn tại, dùng giá mới nếu là item mới
                BigDecimal itemPrice = existingPrices.getOrDefault(menuItem.getId(), menuItem.getPrice());
                
                var item = OrderItem.builder()
                        .order(order)
                        .menuItem(menuItem)
                        .quantity(itemReq.getQuantity())
                        .price(itemPrice) // Sử dụng giá đã xác định
                        .build();
                orderItemRepository.save(item);
            }

            order.setUpdatedAt(LocalDateTime.now()); // Cập nhật thời gian sửa đổi
            orderRepository.save(order);
            messagingTemplate.convertAndSend("/topic/orders", "Order updated for table " + order.getTable().getTableNumber());
            response.put("success", true);
            response.put("message", "Cập nhật order thành công");
            response.put("orderStatus", order.getStatus().toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // GET: Xem chi tiết order (có thể bảo vệ cho staff/admin nếu cần)
    @GetMapping("/detail/{orderId}")
    public String showOrderDetail(@PathVariable Long orderId, Model model) {
        var orderOpt = orderService.getOrderById(orderId);
        if (orderOpt.isEmpty()) {
            model.addAttribute("error", "Order không tồn tại");
            return "error";
        }
        Order order = orderOpt.get();
        model.addAttribute("order", order);
        model.addAttribute("total", orderService.calculateTotal(order));
        return "customer/order-detail"; // Giả định view này tồn tại
    }

    // POST: Cập nhật status order (có thể bảo vệ cho staff/admin)
    @PostMapping("/update-status/{orderId}")
    public String updateOrderStatus(@PathVariable Long orderId,
                                    @RequestParam Order.Status status,
                                    Model model) {
        try {
            orderService.updateOrderStatus(orderId, status);
            return "redirect:/order/detail/" + orderId;
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi cập nhật: " + e.getMessage());
            return "redirect:/order/detail/" + orderId;
        }
    }

    // DTO cho request JSON từ frontend (dùng cho /submit)
    public static class OrderSubmitRequest {
        private String tableNumber;
        private Map<Long, Integer> items; // itemId -> quantity

        public String getTableNumber() { return tableNumber; }
        public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }
        public Map<Long, Integer> getItems() { return items; }
        public void setItems(Map<Long, Integer> items) { this.items = items; }
    }

    // DTO cho update request
    public static class UpdateOrderRequest {
        private Long orderId;
        private List<OrderService.OrderItemRequest> items;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public List<OrderService.OrderItemRequest> getItems() { return items; }
        public void setItems(List<OrderService.OrderItemRequest> items) { this.items = items; }
    }

    // DTO cũ cho form submit (nếu cần giữ cho POST /{tableNumber}, nhưng template dùng /submit nên có thể loại bỏ)
    public static class OrderForm {
        private List<OrderService.OrderItemRequest> orderItems;

        public List<OrderService.OrderItemRequest> getOrderItems() { return orderItems; }
        public void setOrderItems(List<OrderService.OrderItemRequest> orderItems) { this.orderItems = orderItems; }
    }

    @PostMapping("/pay/{orderId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> payOrder(@PathVariable Long orderId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Order order = orderService.payOrder(orderId);
            // Gửi thông báo realtime với thông tin chi tiết hơn
            String tableNumber = order.getTable().getTableNumber();
            String message = "💰 Thanh toán thành công - Bàn " + tableNumber + " (Order #" + order.getId() + ")";
            messagingTemplate.convertAndSend("/topic/orders", message);
            messagingTemplate.convertAndSend("/topic/staff-calls", "Thanh toán: Bàn " + tableNumber);
            response.put("success", true);
            response.put("message", "Thanh toán thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // POST: Gọi nhân viên
    @PostMapping("/call-staff")
    @PreAuthorize("permitAll()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> callStaff(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String tableNumber = request.get("tableNumber");
            
            if (tableNumber == null || tableNumber.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Số bàn không được để trống");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Kiểm tra bàn có tồn tại không
            var tableOpt = tableService.findByTableNumber(tableNumber);
            if (tableOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Bàn không tồn tại");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Gửi thông báo qua WebSocket đến nhân viên
            String message = "🔔 Khách hàng gọi nhân viên tại bàn " + tableNumber;
            messagingTemplate.convertAndSend("/topic/staff-calls", message);
            
            // Cũng gửi vào topic orders để hiển thị trên trang quản lý bàn (với format rõ ràng hơn)
            messagingTemplate.convertAndSend("/topic/orders", "🔔 Gọi nhân viên - Bàn " + tableNumber);
            
            response.put("success", true);
            response.put("message", "Đã gửi yêu cầu gọi nhân viên cho bàn " + tableNumber);
            response.put("tableNumber", tableNumber);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // DELETE: Xóa từng item trong order
    @DeleteMapping("/item/{itemId}")
    @PreAuthorize("permitAll()")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteOrderItem(@PathVariable Long itemId, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Xác định quyền của user
            boolean isEmployee = false;
            boolean isAdmin = false;
            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
                isEmployee = authorities.contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
                isAdmin = authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            
            var itemOpt = orderItemRepository.findById(itemId);
            if (itemOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Món không tồn tại");
                return ResponseEntity.badRequest().body(response);
            }
            
            OrderItem item = itemOpt.get();
            Order order = item.getOrder();
            
            // Kiểm tra quyền xóa: Customer chỉ được xóa khi pending, Employee/Admin được xóa ở mọi trạng thái (trừ paid/done)
            if (!isEmployee && !isAdmin) {
                if (order.getStatus() != Order.Status.pending) {
                    response.put("success", false);
                    response.put("message", "Không thể xóa món trong đơn hàng đã được xử lý");
                    return ResponseEntity.badRequest().body(response);
                }
            } else {
                if (order.getStatus() == Order.Status.paid || order.getStatus() == Order.Status.done) {
                    response.put("success", false);
                    response.put("message", "Không thể xóa món trong đơn hàng đã thanh toán");
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            // Xóa item
            orderItemRepository.delete(item);
            
            // Cập nhật thời gian order
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            
            messagingTemplate.convertAndSend("/topic/orders", "Order item deleted from table " + order.getTable().getTableNumber());
            response.put("success", true);
            response.put("message", "Xóa món thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // GET: Lấy order hiện tại của bàn (cho khách hàng)
    @GetMapping("/current/{tableNumber}")
    @PreAuthorize("permitAll()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCurrentOrder(@PathVariable String tableNumber) {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println("=== getCurrentOrder called for table: " + tableNumber + " ===");
            
            Optional<Order> orderOpt = orderService.getCurrentOrderByTable(tableNumber);
            if (orderOpt.isEmpty()) {
                System.out.println("No current order found for table: " + tableNumber);
                response.put("success", false);
                response.put("message", "Không có đơn hàng nào");
                response.put("order", null);
                return ResponseEntity.ok(response);
            }
            
            Order order = orderOpt.get();
            
            // QUAN TRỌNG: Kiểm tra lại tableNumber để đảm bảo đúng bàn
            String orderTableNumber = order.getTable().getTableNumber();
            if (!orderTableNumber.equals(tableNumber)) {
                System.out.println("ERROR: Order belongs to table " + orderTableNumber + " but requested table " + tableNumber);
                response.put("success", false);
                response.put("message", "Lỗi: Order không thuộc bàn này");
                response.put("order", null);
                return ResponseEntity.badRequest().body(response);
            }
            
            System.out.println("Found order ID=" + order.getId() + " for table " + tableNumber + ", status=" + order.getStatus());
            
            // Tạo DTO để tránh circular reference
            Map<String, Object> orderDTO = new HashMap<>();
            orderDTO.put("id", order.getId());
            orderDTO.put("status", order.getStatus().toString());
            orderDTO.put("createdAt", order.getCreatedAt().toString());
            orderDTO.put("updatedAt", order.getUpdatedAt().toString());
            orderDTO.put("tableNumber", orderTableNumber);
            
            // Convert items to DTO
            List<Map<String, Object>> itemsDTO = new ArrayList<>();
            if (order.getItems() != null) {
                for (var item : order.getItems()) {
                    Map<String, Object> itemDTO = new HashMap<>();
                    itemDTO.put("id", item.getId());
                    itemDTO.put("quantity", item.getQuantity());
                    itemDTO.put("price", item.getPrice());
                    
                    Map<String, Object> menuItemDTO = new HashMap<>();
                    menuItemDTO.put("id", item.getMenuItem().getId());
                    menuItemDTO.put("name", item.getMenuItem().getName());
                    menuItemDTO.put("price", item.getMenuItem().getPrice());
                    menuItemDTO.put("imageUrl", item.getMenuItem().getImageUrl());
                    itemDTO.put("menuItem", menuItemDTO);
                    
                    itemsDTO.add(itemDTO);
                }
            }
            orderDTO.put("items", itemsDTO);
            
            response.put("success", true);
            response.put("order", orderDTO);
            response.put("total", orderService.calculateTotal(order));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

}
