package com.example.Lumi.model;


import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "check_in_out")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInOut {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Gắn với nhân viên
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime;

    private String checkInLocation;
    private String checkInPhoto;

    private LocalDateTime checkOutTime;
    private String checkOutLocation;
}
