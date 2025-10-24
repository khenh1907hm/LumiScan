package com.example.Lumi.repository;

import com.example.Lumi.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Tìm order mới nhất theo tableId (cho bàn đó)
    Optional<Order> findTopByTableIdOrderByIdDesc(Long tableId);

    // Tìm order theo ID và status (ví dụ: để cập nhật)
    Optional<Order> findByIdAndStatus(Long id, Order.Status status);
}