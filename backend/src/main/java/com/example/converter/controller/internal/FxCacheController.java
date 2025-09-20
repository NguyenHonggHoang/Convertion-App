package com.example.converter.controller.internal;

import com.example.converter.service.cache.FxRateCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fx-cache")
@Profile("dev | test")
@RequiredArgsConstructor
public class FxCacheController {

    private final FxRateCacheService fxRateCacheService;

    @PostMapping("/cache/refresh/{baseCurrency}")
    public ResponseEntity<Map<String, Object>> refresh(@PathVariable String baseCurrency) {
        Map<String, Double> rates = fxRateCacheService.refreshBaseRates(baseCurrency);
        if (rates == null || rates.isEmpty()) {
            return ResponseEntity.status(502).body(Map.of(
                    "status", "error",
                    "message", "Failed to fetch rates for base currency: " + baseCurrency,
                    "baseCurrency", baseCurrency.toUpperCase()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "baseCurrency", baseCurrency.toUpperCase(),
                "count", rates.size()
        ));
    }

//    public ResponseEntity<Map<String, Object>> getRates(@PathVariable String base, @PathVariable String quote){
//        double r = fxRateCacheService.getRate(base, quote);
//        if (r < 0) {
//            return ResponseEntity.status(404).body(Map.of(
//                    "status", "error",
//                    "message", "Rate not found for " + base + " to " + quote,
//                    "baseCurrency", base.toUpperCase(),
//                    "targetCurrency", quote.toUpperCase()
//            ));
//        }
//        return ResponseEntity.ok(Map.of(
//                "status", "success",
//                "baseCurrency", base.toUpperCase(),
//                "targetCurrency", quote.toUpperCase(),
//                "rate", r
//        ));
//    }
}
