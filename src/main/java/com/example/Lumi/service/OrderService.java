package com.example.Lumi.service;

import com.example.Lumi.model.MenuItem;
import com.example.Lumi.model.Order;
import com.example.Lumi.model.OrderItem;
import com.example.Lumi.model.TableEntity;
import com.example.Lumi.repository.OrderRepository;
import com.example.Lumi.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemService menuItemService;
    private final TableService tableService;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        MenuItemService menuItemService,
                        TableService tableService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemService = menuItemService;
        this.tableService = tableService;
    }

    @Transactional
    public Order createOrder(String tableNumber, List<OrderItemRequest> orderItemRequests) {
        // Tìm TableEntity theo tableNumber
        Optional<TableEntity> tableOpt = tableService.findByTableNumber(tableNumber);
        if (tableOpt.isEmpty() || !"available".equalsIgnoreCase(tableOpt.get().getStatus())) {
            throw new IllegalArgumentException("Bàn không khả dụng");
        }
        TableEntity table = tableOpt.get();

        // Tạo Order
        Order order = Order.builder()
                .table(table)
                .status(Order.Status.pending)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);
        // Tạo OrderItems
        for (OrderItemRequest req : orderItemRequests) {
            MenuItem menuItem = menuItemService.getMenuItemByIdOrThrow(req.getMenuItemId()); // Sửa ở đây
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .menuItem(menuItem)
                    .quantity(req.getQuantity())
                    .price(menuItem.getPrice()) // Giá từ MenuItem
                    .build();
            orderItemRepository.save(item);
        }

        // Cập nhật trạng thái bàn thành "occupied" (giả định TableEntity có status)
        tableService.updateTableStatus(tableNumber, "occupied");

        return order;
    }

    // Cập nhật status của order (ví dụ: từ pending sang preparing)
    @Transactional
    public Order updateOrderStatus(Long orderId, Order.Status newStatus) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order không tồn tại");
        }
        Order order = orderOpt.get();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    // Lấy order theo ID
    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    // Tính tổng tiền của order (tùy chọn)
    public BigDecimal calculateTotal(Order order) {
        return order.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // DTO cho request
    public static class OrderItemRequest {
        private Long menuItemId;
        private int quantity;

        // Getters và setters
        public Long getMenuItemId() { return menuItemId; }
        public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}