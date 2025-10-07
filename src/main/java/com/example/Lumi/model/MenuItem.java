package com.example.Lumi.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private String imageUrl;

    @Column(length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('available','out_of_stock')")
    private Status status = Status.available;

    public enum Status {
        available,
        out_of_stock
    }
}
