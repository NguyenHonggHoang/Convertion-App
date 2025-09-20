package com.example.converter.dto.conversion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for unit conversion response.
 * Fields: convertedValue (double), toUnit (string).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitConversionResponse {

    private Double convertedValue;
    private String toUnit;
} 