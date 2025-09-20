package com.example.converter.repository;

import com.example.converter.entity.ExchangeRateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for ExchangeRateHistory Entity.
 * Entity ExchangeRateHistory maps to the exchange_rate_history table with fields: id, baseCurrency, targetCurrency, rate, recordedAt.
 * Provides method to save exchange rate history.
 */
@Repository
public interface ExchangeRateHistoryRepository extends JpaRepository<ExchangeRateHistory, Long> {

    /**
     * Find the latest exchange rate for a currency pair
     * @param baseCurrency the base currency
     * @param targetCurrency the target currency
     * @return Optional containing the latest exchange rate
     */
    Optional<ExchangeRateHistory> findFirstByBaseCurrencyAndTargetCurrencyOrderByRecordedAtDesc(
            String baseCurrency, String targetCurrency);

    /**
     * Find exchange rate history for a currency pair in a date range
     * @param baseCurrency the base currency
     * @param targetCurrency the target currency
     * @param fromDate the start date
     * @param toDate the end date
     * @return List of exchange rate history
     */
    List<ExchangeRateHistory> findByBaseCurrencyAndTargetCurrencyAndRecordedAtBetweenOrderByRecordedAtDesc(
            String baseCurrency, String targetCurrency, LocalDateTime fromDate, LocalDateTime toDate);

    /**
     * Find all exchange rates for a base currency
     * @param baseCurrency the base currency
     * @return List of exchange rates
     */
    List<ExchangeRateHistory> findByBaseCurrencyOrderByRecordedAtDesc(String baseCurrency);

    /**
     * Find history for predict service with date range filtering
     */
    @Query("""
      select h from ExchangeRateHistory h
      where upper(h.baseCurrency)=upper(:base) and upper(h.targetCurrency)=upper(:quote)
        and (:from is null or cast(h.recordedAt as date) >= :from)
        and (:to is null or cast(h.recordedAt as date) <= :to)
      order by h.recordedAt asc
    """)
    List<ExchangeRateHistory> findHistoryForPredict(
        @Param("base") String base,
        @Param("quote") String quote,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
} 