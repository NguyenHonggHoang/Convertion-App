package com.example.converter.controller.conversion;

import com.example.converter.dto.conversion.CurrencyConversionRequest;
import com.example.converter.dto.conversion.CurrencyConversionResponse;
import com.example.converter.service.currency.CurrencyConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Spring Boot REST Controller for currency conversion functionality.
 * Endpoint: /api/convert/currency (POST).
 * Input: JSON with amount (double), fromCurrency (string), toCurrency (string).
 * Output: JSON containing convertedAmount (double), toCurrency (string), exchangeRate (double), and predictionData (list of predicted rates).
 * Uses CurrencyConversionService to handle business logic.
 * Includes Swagger annotations for this API.
 */
@RestController
@RequestMapping("/api/convert")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Currency Conversion", description = "APIs for currency conversion")
public class CurrencyConversionController {

    private final CurrencyConversionService currencyConversionService;

    @PostMapping("/currency")
    @Operation(summary = "Convert currency", description = "Convert an amount from one currency to another with exchange rate and predictions")
    public ResponseEntity<CurrencyConversionResponse> convertCurrency(@Valid @RequestBody CurrencyConversionRequest request, Authentication authentication) {
        log.info("Received currency conversion request: {} {} to {}", 
                request.getAmount(), request.getFromCurrency(), request.getToCurrency());

        try {
            CurrencyConversionResponse response = currencyConversionService.convertCurrency(request, authentication);
            
            log.info("Currency conversion successful: {} {} = {} {}", 
                    request.getAmount(), request.getFromCurrency(), response.getConvertedAmount(), response.getToCurrency());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Currency conversion failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error during currency conversion: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
} 