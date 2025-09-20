package com.example.converter.service.currency;

import com.example.converter.dto.conversion.CurrencyConversionRequest;
import com.example.converter.dto.conversion.CurrencyConversionResponse;
import com.example.converter.entity.ExchangeRateHistory;
import com.example.converter.entity.User;
import com.example.converter.repository.ExchangeRateHistoryRepository;
import com.example.converter.repository.NewsArticleRepository;
import com.example.converter.repository.UserRepository;
import com.example.converter.security.UserPrincipal;
import com.example.converter.service.cache.FxRateCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Boot Service for currency conversion functionality.
 * Method: convertCurrency(double amount, String fromCurrency, String toCurrency).
 * Logic:
 *   1. Save exchange rate to Redis Cache and exchange_rate_history table through ExchangeRateHistoryRepository.
 *   2. Call Python Microservice /predict to get exchange rate prediction data.
 *   3. Return conversion result and prediction data.
 * Handles invalid currencies or errors from external API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyConversionService {

    private final ExchangeRateHistoryRepository exchangeRateHistoryRepository;
    private final WebClient webClient;
    private final NewsArticleRepository newsArticleRepository;
    private final FxRateCacheService fxRateCacheService;
    private final UserRepository userRepository;

    @Value("${microservice.predict.url}")
    private String predictServiceUrl;


    /**
     * Convert currency from one currency to another
     * @param request the conversion request
     * @param authentication the authentication object to get user ID
     * @return CurrencyConversionResponse with converted amount and prediction data
     */
    public CurrencyConversionResponse convertCurrency(CurrencyConversionRequest request, Authentication authentication) {
        log.info("Converting {} {} to {}", request.getAmount(), request.getFromCurrency(), request.getToCurrency());

        try {
            double exchangeRate = fxRateCacheService.getRate(request.getFromCurrency(), request.getToCurrency());

            if (exchangeRate < 0) {
                log.error("Invalid exchange rate for {} to {}: {}", request.getFromCurrency(), request.getToCurrency(), exchangeRate);
                throw new IllegalArgumentException("Invalid exchange rate for " + request.getFromCurrency() + " to " + request.getToCurrency());
            }

            double convertedAmount = request.getAmount() * exchangeRate;

            Long userId = null;
            if (authentication != null && authentication.isAuthenticated()) {
                try {
                    String username = authentication.getName();
                    Object principal = authentication.getPrincipal();
                    log.debug("Authentication details - isAuthenticated: {}, name: {}, principal type: {}", 
                            authentication.isAuthenticated(), username, principal.getClass().getSimpleName());
                    
                    if (principal instanceof UserPrincipal) {
                        UserPrincipal userPrincipal = (UserPrincipal) principal;
                        userId = userPrincipal.getId();
                        log.debug("Found UserPrincipal with ID: {} for username: {}", userId, username);
                    } else {
                        log.debug("Principal is not UserPrincipal, looking up by username: {}", username);
                        userId = getUserIdByUsername(username);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get user ID: {}", e.getMessage());
                }
            } else {
                log.debug("No authentication or not authenticated - authentication: {}", authentication);
            }

            saveExchangeRateHistory(request.getFromCurrency(), request.getToCurrency(), exchangeRate, userId);

            List<Map<String, Object>> predictionData = getPredictionData(request.getFromCurrency(), request.getToCurrency());

            double sentimentAdj = getRecentSentimentAdjustment();

            log.info("Successfully converted {} {} to {} {}", 
                    request.getAmount(), request.getFromCurrency(), convertedAmount, request.getToCurrency());
            
            return new CurrencyConversionResponse(convertedAmount, request.getToCurrency(), exchangeRate, predictionData);
            
        } catch (Exception e) {
            log.error("Currency conversion error: {}", e.getMessage());
            throw new RuntimeException("Failed to convert currency: " + e.getMessage());
        }
    }


    /**
     * Save exchange rate history
     * @param baseCurrency the base currency
     * @param targetCurrency the target currency
     * @param rate the exchange rate
     * @param userId the user ID (can be null for anonymous users)
     */
    private void saveExchangeRateHistory(String baseCurrency, String targetCurrency, double rate, Long userId) {
        try {
            ExchangeRateHistory history = new ExchangeRateHistory();
            history.setBaseCurrency(baseCurrency);
            history.setTargetCurrency(targetCurrency);
            history.setRate(BigDecimal.valueOf(rate));
            history.setUserId(userId);
            history.setRecordedAt(LocalDateTime.now());
            
            exchangeRateHistoryRepository.save(history);
            log.info("Saved exchange rate history: {} to {} = {} for user: {}", baseCurrency, targetCurrency, rate, userId);
        } catch (Exception e) {
            log.error("Failed to save exchange rate history: {}", e.getMessage());
        }
    }

    /**
     * Get prediction data from Python microservice
     * @param baseCurrency the base currency
     * @param targetCurrency the target currency
     * @return list of prediction data
     */
    private List<Map<String, Object>> getPredictionData(String baseCurrency, String targetCurrency) {
        log.info("Prediction data generation moved to frontend - returning empty list");
        return List.of();
    }

    /**
     * Get recent sentiment adjustment based on news articles
     * @return sentiment adjustment value
     */

    private double getRecentSentimentAdjustment() {
        try {
            var latest = newsArticleRepository.findTop10ByOrderByPublishedAtDesc();
            if (latest == null || latest.isEmpty()) return 0.0;
            double avg = latest.stream()
                    .map(n -> n.getSentimentScore() == null ? 0.0 : n.getSentimentScore())
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
            return (avg - 0.33) * 0.03;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Get user ID by username
     * @param username the username
     * @return user ID or null if not found
     */
    private Long getUserIdByUsername(String username) {
        try {
            log.debug("Looking up user ID for username: {}", username);
            Optional<User> userOptional = userRepository.findByUsername(username);
            if (userOptional.isPresent()) {
                Long userId = userOptional.get().getId();
                log.debug("Found user ID {} for username: {}", userId, username);
                return userId;
            } else {
                log.warn("No user found for username: {}", username);
                return null;
            }
        } catch (Exception e) {
            log.error("Error getting user ID for username {}: {}", username, e.getMessage(), e);
            return null;
        }
    }
}
