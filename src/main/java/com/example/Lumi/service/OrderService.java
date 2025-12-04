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
    @Transactional
    public Order payOrder(Long orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order không tồn tại");
        }
        Order order = orderOpt.get();
        
        // Cho phép thanh toán từ bất kỳ status nào (pending, preparing, served)
        // Không cần check status nữa
        
        // Cập nhật status order
        order.setStatus(Order.Status.paid);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Cập nhật status bàn về available
        tableService.updateTableStatus(order.getTable().getTableNumber(), "available");

        return order;
    }

    // Lấy order hiện tại của bàn (chưa thanh toán và còn món)
    public Optional<Order> getCurrentOrderByTable(String tableNumber) {
        Optional<TableEntity> tableOpt = tableService.findByTableNumber(tableNumber);
        if (tableOpt.isEmpty()) {
            System.out.println("Table not found: " + tableNumber);
            return Optional.empty();
        }
        TableEntity table = tableOpt.get();
        System.out.println("Finding current order for table ID=" + table.getId() + ", tableNumber=" + table.getTableNumber());
        
        // Tìm order mới nhất của bàn này, còn ở trạng thái khách đang đặt (pending)
        Optional<Order> orderOpt = orderRepository.findTopByTableIdOrderByIdDesc(table.getId());
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            
            // QUAN TRỌNG: Kiểm tra lại tableNumber để đảm bảo đúng bàn (tránh lỗi mapping)
            if (order.getTable() == null || !order.getTable().getTableNumber().equals(tableNumber)) {
                System.out.println("ERROR: Order " + order.getId() + " belongs to different table!");
                return Optional.empty();
            }
            
            // Chỉ coi là "current" cho khách nếu đơn còn ở trạng thái pending
            // và thực sự còn món (kiểm tra trực tiếp DB để tránh cache cũ)
            boolean isPending = order.getStatus() == Order.Status.pending;
            boolean hasItems = !orderItemRepository.findByOrder(order).isEmpty();
            
            System.out.println("Order " + order.getId() + ": isPending=" + isPending + ", hasItems=" + hasItems);
            
            if (isPending && hasItems) {
                return Optional.of(order);
            }
        } else {
            System.out.println("No order found for table ID=" + table.getId());
        }
        return Optional.empty();
    }

    // Thêm món vào order hiện có (cho khách hàng)
    @Transactional
    public Order addItemsToOrder(Long orderId, List<OrderItemRequest> orderItemRequests) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order không tồn tại");
        }
        Order order = orderOpt.get();
        
        // Kiểm tra order chưa thanh toán
        if (order.getStatus() == Order.Status.paid || order.getStatus() == Order.Status.done) {
            throw new IllegalArgumentException("Không thể thêm món vào đơn hàng đã thanh toán");
        }
        
        // Thêm các món mới vào order
        for (OrderItemRequest req : orderItemRequests) {
            MenuItem menuItem = menuItemService.getMenuItemByIdOrThrow(req.getMenuItemId());
            
            // Kiểm tra xem món này đã có trong order chưa
            boolean itemExists = false;
            if (order.getItems() != null) {
                for (OrderItem existingItem : order.getItems()) {
                    if (existingItem.getMenuItem().getId().equals(menuItem.getId())) {
                        // Nếu đã có, tăng số lượng
                        existingItem.setQuantity(existingItem.getQuantity() + req.getQuantity());
                        orderItemRepository.save(existingItem);
                        itemExists = true;
                        break;
                    }
                }
            }
            
            // Nếu chưa có, tạo mới
            if (!itemExists) {
                OrderItem item = OrderItem.builder()
                        .order(order)
                        .menuItem(menuItem)
                        .quantity(req.getQuantity())
                        .price(menuItem.getPrice())
                        .build();
                orderItemRepository.save(item);
            }
        }
        
        // Cập nhật thời gian
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    // Xóa tất cả items trong order (cho khách hàng xóa giỏ hàng)
    @Transactional
    public Order clearOrderItems(Long orderId) {
        return clearOrderItems(orderId, false);
    }
    
    // Xóa tất cả items trong order (overload với allowNonPending cho employee/admin)
    @Transactional
    public Order clearOrderItems(Long orderId, boolean allowNonPending) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order không tồn tại");
        }
        Order order = orderOpt.get();
        
        // Kiểm tra order phải ở trạng thái pending (trừ khi allowNonPending = true)
        if (!allowNonPending && order.getStatus() != Order.Status.pending) {
            throw new IllegalArgumentException("Không thể xóa món trong đơn hàng đã được xử lý");
        }
        
        // Employee/Admin không được xóa order đã thanh toán
        if (allowNonPending && (order.getStatus() == Order.Status.paid || order.getStatus() == Order.Status.done)) {
            throw new IllegalArgumentException("Không thể xóa món trong đơn hàng đã thanh toán");
        }
        
        // Xóa tất cả items
        orderItemRepository.deleteByOrder(order);
        if (order.getItems() != null) {
            order.getItems().clear();
        }
        
        // Cập nhật thời gian
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }
}