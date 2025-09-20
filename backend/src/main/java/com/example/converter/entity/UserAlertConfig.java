package com.example.converter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UserAlertConfig entity representing the user_alert_config table.
 * Maps to the user_alert_config table with fields: id, userId, baseCurrency, targetCurrency, alertType, thresholdRate, isActive, createdAt.
 * Has Many-to-One relationship with User Entity through userId.
 */
@Entity
@Table(name = "user_alert_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAlertConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

    @Column(name = "target_currency", length = 3, nullable = false)
    private String targetCurrency;

    @Column(name = "alert_type", length = 20, nullable = false)
    private String alertType;

    @Column(name = "threshold_rate", precision = 10, scale = 4, nullable = false)
    private BigDecimal thresholdRate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
} 