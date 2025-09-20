package com.example.converter.dto.conversion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for unit conversion request.
 * Fields: value (double), fromUnit (string), toUnit (string).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitConversionRequest {

    @NotNull(message = "Value is required")
    private Double value;

    @NotNull(message = "From unit is required")
    @Size(min = 1, max = 50, message = "From unit must be between 1 and 50 characters")
    private String fromUnit;

    @NotNull(message = "To unit is required")
    @Size(min = 1, max = 50, message = "To unit must be between 1 and 50 characters")
    private String toUnit;
} 