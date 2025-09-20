package com.example.converter.dto.conversion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for currency conversion request.
 * Fields: amount (double), fromCurrency (string), toCurrency (string).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyConversionRequest {

    @NotNull(message = "Amount is required")
    private Double amount;

    @NotNull(message = "From currency is required")
    @Size(min = 3, max = 3, message = "From currency must be exactly 3 characters")
    private String fromCurrency;

    @NotNull(message = "To currency is required")
    @Size(min = 3, max = 3, message = "To currency must be exactly 3 characters")
    private String toCurrency;
} 