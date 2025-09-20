package com.example.converter.service.cache;

import com.example.converter.service.external.FxApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Isolated cache facade for base FX rates. Keeping @Cacheable here avoids
 * self-invocation issues when called from other services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BaseRatesCacheService {

    private final FxApiClient fxApiClient;

    @Cacheable(
            value = "baseRates",
            key = "#baseCurrency?.trim().toUpperCase()",
            unless = "#result == null || #result.isEmpty()",
            cacheManager = "cacheManager",
            condition = "#baseCurrency != null && !#baseCurrency.isEmpty()"
    )
    public Map<String, Double> getBaseRates(String baseCurrency){
        log.info("Cache MISS - Fetching base rates from API for currency: {}", baseCurrency);
        return fxApiClient.fetchBaseRates(baseCurrency);
    }

    @CachePut (
            value = "baseRates",
            key = "#baseCurrency?.trim().toUpperCase()",
            unless = "#result == null || #result.isEmpty()",
            cacheManager = "cacheManager",
            condition = "#baseCurrency != null && !#baseCurrency.isEmpty()"
    )
    public Map<String, Double> refreshBaseRates(String baseCurrency) {
        log.info("Refreshing base rates for currency: {}", baseCurrency);
        return fxApiClient.fetchBaseRates(baseCurrency);
    }
}
