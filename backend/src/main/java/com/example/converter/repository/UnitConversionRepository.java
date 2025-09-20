package com.example.converter.repository;

import com.example.converter.entity.UnitConversionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for UnitConversionLog Entity.
 * Entity UnitConversionLog maps to the unit_conversion_log table with fields: id, userId, fromUnit, toUnit, inputValue, outputValue, convertedAt.
 * Provides method to save conversion log.
 */
@Repository
public interface UnitConversionRepository extends JpaRepository<UnitConversionLog, Long> {

    /**
     * Find conversion logs by user ID
     * @param userId the user ID to search for
     * @return List of conversion logs for the user
     */
    List<UnitConversionLog> findByUserIdOrderByConvertedAtDesc(Long userId);

    /**
     * Find conversion logs by user ID and date range
     * @param userId the user ID to search for
     * @param fromDate the start date
     * @param toDate the end date
     * @return List of conversion logs for the user in the date range
     */
    List<UnitConversionLog> findByUserIdAndConvertedAtBetweenOrderByConvertedAtDesc(
            Long userId, java.time.LocalDateTime fromDate, java.time.LocalDateTime toDate);
} 