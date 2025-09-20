package com.example.converter.repository;

import com.example.converter.entity.UserAlertConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for UserAlertConfig Entity.
 * Entity UserAlertConfig maps to the user_alert_config table with fields: id, userId, baseCurrency, targetCurrency, alertType, thresholdRate, isActive, createdAt.
 * Provides methods to save and query alert configurations.
 */
@Repository
public interface UserAlertConfigRepository extends JpaRepository<UserAlertConfig, Long> {

    /**
     * Find all alert configurations for a user
     * @param userId the user ID
     * @return List of alert configurations for the user
     */
    List<UserAlertConfig> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find active alert configurations for a user
     * @param userId the user ID
     * @return List of active alert configurations for the user
     */
    List<UserAlertConfig> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    /**
     * Find alert configurations by user and currency pair
     * @param userId the user ID
     * @param baseCurrency the base currency
     * @param targetCurrency the target currency
     * @return List of alert configurations for the user and currency pair
     */
    List<UserAlertConfig> findByUserIdAndBaseCurrencyAndTargetCurrency(Long userId, String baseCurrency, String targetCurrency);

    /**
     * Find all active alert configurations
     * @return List of all active alert configurations
     */
    List<UserAlertConfig> findByIsActiveTrue();
} 