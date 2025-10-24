package com.example.Lumi.repository;

import com.example.Lumi.model.Order;
import com.example.Lumi.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    void deleteByOrder(Order order);
    // Optional: Lấy items theo order (nếu cần)
    List<OrderItem> findByOrder(Order order);
}