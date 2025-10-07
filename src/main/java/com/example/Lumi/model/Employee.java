package com.example.Lumi.model;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mỗi employee gắn với 1 user
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, columnDefinition = "ENUM('active','inactive')")
    private Status status = Status.active;

    public enum Status {
        active,
        inactive
    }
}
