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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

            // Kiểm tra trạng thái bàn - nếu occupied thì hiển thị ảnh table_in_use
            if (!"available".equalsIgnoreCase(table.get().getStatus())) {
                System.out.println("WARNING: Table status is not available: " + table.get().getStatus());
                model.addAttribute("tableNumber", tableNumber);
                model.addAttribute("tableStatus", table.get().getStatus());
                return "customer/table-in-use";
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
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitOrder(@RequestBody OrderSubmitRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Chuyển đổi items từ Map<Long, Integer> thành List<OrderItemRequest>
            List<OrderService.OrderItemRequest> orderItems = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : request.getItems().entrySet()) {
                OrderService.OrderItemRequest itemReq = new OrderService.OrderItemRequest();
                itemReq.setMenuItemId(entry.getKey());
                itemReq.setQuantity(entry.getValue());
                orderItems.add(itemReq);
            }
            // Tạo order
            System.out.println("=== Creating order for table: " + request.getTableNumber() + " ===");
            System.out.println("Order items count: " + orderItems.size());
            Order order = orderService.createOrder(request.getTableNumber(), orderItems);
            System.out.println("Order created successfully! Order ID: " + order.getId());
            System.out.println("Order status: " + order.getStatus());
            System.out.println("Order items count: " + (order.getItems() != null ? order.getItems().size() : 0));

            // Gửi thông báo realtime đến employee
            messagingTemplate.convertAndSend("/topic/orders",
                    "New order from table " + request.getTableNumber() + " - Order ID: " + order.getId());

            response.put("success", true);
            response.put("message", "Đặt món thành công! Mã đơn: " + order.getId());
            response.put("orderId", order.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
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

    // POST: Cập nhật items trong order (cho employee)
    @PostMapping("/update-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateOrderItems(@RequestBody UpdateOrderRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            var orderOpt = orderService.getOrderById(request.getOrderId());
            if (orderOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Order không tồn tại");
                return ResponseEntity.badRequest().body(response);
            }
            Order order = orderOpt.get();
            // Xóa items cũ và thêm mới
            orderItemRepository.deleteByOrder(order);
            for (OrderService.OrderItemRequest itemReq : request.getItems()) {
                var menuItem = menuItemService.getMenuItemByIdOrThrow(itemReq.getMenuItemId());
                var item = OrderItem.builder()
                        .order(order)
                        .menuItem(menuItem)
                        .quantity(itemReq.getQuantity())
                        .price(menuItem.getPrice())
                        .build();
                orderItemRepository.save(item);
            }
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            // Gửi notification realtime
            messagingTemplate.convertAndSend("/topic/orders", "Order updated for table " + order.getTable().getTableNumber());
            response.put("success", true);
            response.put("message", "Cập nhật order thành công");
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
            // Gửi thông báo realtime
            messagingTemplate.convertAndSend("/topic/orders", "Order paid for table " + order.getTable().getTableNumber());
            response.put("success", true);
            response.put("message", "Thanh toán thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

}
