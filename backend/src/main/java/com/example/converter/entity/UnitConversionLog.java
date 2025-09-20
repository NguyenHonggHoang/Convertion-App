package com.example.converter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UnitConversionLog entity representing the unit_conversion_log table.
 * Maps to the unit_conversion_log table with fields: id, userId, fromUnit, toUnit, inputValue, outputValue, convertedAt.
 * Has Many-to-One relationship with User Entity through userId.
 */
@Entity
@Table(name = "unit_conversion_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitConversionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "from_unit", length = 50, nullable = false)
    private String fromUnit;

    @Column(name = "to_unit", length = 50, nullable = false)
    private String toUnit;

    @Column(name = "input_value", precision = 18, scale = 8, nullable = false)
    private BigDecimal inputValue;

    @Column(name = "output_value", precision = 18, scale = 8, nullable = false)
    private BigDecimal outputValue;

    @CreationTimestamp
    @Column(name = "converted_at", nullable = false, updatable = false)
    private LocalDateTime convertedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
} 