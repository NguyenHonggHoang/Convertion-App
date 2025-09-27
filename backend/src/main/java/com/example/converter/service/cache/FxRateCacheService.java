package com.example.converter.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FxRateCacheService {
    private final BaseRatesCacheService baseRatesCacheService;
    private final CacheManager cacheManager;


    public Map<String, Double> refreshBaseRates(String baseCurrency) {
        return baseRatesCacheService.refreshBaseRates(baseCurrency);
    }

    @SuppressWarnings("unchecked")
    public double getRate(String baseCurrency, String quoteCurrency){
        String base = baseCurrency.trim().toUpperCase();
        String quote = quoteCurrency.trim().toUpperCase();
        log.info("Fetching exchange rate for {} to {}", base, quote);

        if(base.equals(quote)) return 1.0;

        log.debug("Looking up base rates for: {}", base);

        Map<String, Double> rates = null;
        Cache cache = (cacheManager != null) ? cacheManager.getCache("baseRates") : null;
        if (cache != null) {
            Cache.ValueWrapper vw = cache.get(base);
            if (vw != null && vw.get() instanceof Map) {
                log.info("Cache HIT for baseRates key={}", base);
                rates = (Map<String, Double>) vw.get();
            } else {
                log.info("Cache MISS for baseRates key={}", base);
                rates = baseRatesCacheService.getBaseRates(base);
            }
        } else {
            log.warn("No cache available (cacheManager is null). Falling back to API call.");
            rates = baseRatesCacheService.getBaseRates(base);
        }

        log.debug("Retrieved rates for {}: {}", base, rates);

        Double v = (rates != null) ? rates.get(quote) : null;
        if( v != null && v > 0){
            log.info("Found API rate for {} to {}: {}", base, quote, v);
            return v;
        }

        log.warn("API rate not found, using MOCK rate for {} to {}", base, quote);
        Double mockRate = MOCK.get(base).get(quote);
        return (mockRate != null && mockRate > 0) ? mockRate : -1.0;
    }

    private static final Map<String, Map<String, Double>> MOCK = Map.of(
            "USD", Map.of("EUR", 0.85, "JPY", 110.0, "GBP", 0.73, "VND", 23000.0),
            "EUR", Map.of("USD", 1.18, "JPY", 129.0, "GBP", 0.86, "VND", 27000.0),
            "JPY", Map.of("USD", 0.009, "EUR", 0.0077, "GBP", 0.0066, "VND", 209.0),
            "GBP", Map.of("USD", 1.37, "EUR", 1.16, "JPY", 151.0, "VND", 31500.0),
            "VND", Map.of("USD", 0.000043, "EUR", 0.000037, "JPY", 0.0048, "GBP", 0.000032)
    );
}
