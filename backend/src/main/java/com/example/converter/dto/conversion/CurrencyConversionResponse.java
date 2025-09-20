package com.example.converter.dto.conversion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for currency conversion response.
 * Fields: convertedAmount (double), toCurrency (string), exchangeRate (double), predictionData (List<Map<String,Object>>).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyConversionResponse {

    private Double convertedAmount;
    private String toCurrency;
    private Double exchangeRate;
    private List<Map<String, Object>> predictionData;
}
