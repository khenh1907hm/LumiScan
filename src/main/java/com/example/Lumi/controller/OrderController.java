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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        try {
            // Kiểm tra bàn tồn tại
            var table = tableService.findByTableNumber(tableNumber);
            if (table.isEmpty()) {
                model.addAttribute("error", "Bàn không tồn tại");
                return "error";
            }

            // Kiểm tra trạng thái bàn
            if (!"available".equalsIgnoreCase(table.get().getStatus())) {
                model.addAttribute("error", "Bàn đang được sử dụng");
                return "error";
            }

            // Đưa dữ liệu ra giao diện
            model.addAttribute("tableNumber", tableNumber);
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("menuItems", menuItemService.getAllMenuItems());

            return "customer/order";

        } catch (Exception e) {
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
            Order order = orderService.createOrder(request.getTableNumber(), orderItems);

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
            var tableOpt = tableService.findByTableNumber(tableNumber);
            if (tableOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Bàn không tồn tại");
                return ResponseEntity.badRequest().body(response);
            }
            var table = tableOpt.get();
            // Lấy order pending hôm nay (giả sử repo có method này)
            List<Order> orders = orderRepository.findByTableAndStatusAndCreatedAt(table, Order.Status.pending, LocalDate.now());
            if (orders.isEmpty()) {
                response.put("success", false);
                response.put("message", "Không có order nào");
                return ResponseEntity.ok(response);
            }
            Order order = orders.get(0); // Giả sử 1 bàn 1 order active
            response.put("success", true);
            response.put("order", order);
            response.put("total", orderService.calculateTotal(order));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
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
}
