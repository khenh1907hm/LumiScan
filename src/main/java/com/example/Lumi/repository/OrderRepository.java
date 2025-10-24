package com.example.Lumi.repository;

import com.example.Lumi.model.Order;
import com.example.Lumi.model.TableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Tìm order mới nhất theo tableId (cho bàn đó)
    Optional<Order> findTopByTableIdOrderByIdDesc(Long tableId);

    // Tìm order theo ID và status (ví dụ: để cập nhật)
    Optional<Order> findByIdAndStatus(Long id, Order.Status status);

    // Thêm method để lấy order theo bàn, status, và ngày tạo (cho controller)
    @Query("SELECT o FROM Order o WHERE o.table = :table AND o.status = :status AND DATE(o.createdAt) = :date")
    List<Order> findByTableAndStatusAndCreatedAt(@Param("table") TableEntity table, @Param("status") Order.Status status, @Param("date") LocalDate date);
}
