package com.example.converter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ExchangeRateHistory entity representing the exchange_rate_history table.
 * Maps to the exchange_rate_history table with fields: id, baseCurrency, targetCurrency, rate, recordedAt.
 */
@Entity
@Table(name = "exchange_rate_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

    @Column(name = "target_currency", length = 3, nullable = false)
    private String targetCurrency;

    @Column(name = "rate", precision = 10, scale = 4, nullable = false)
    private BigDecimal rate;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    // Relationship with User entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
    }
} 