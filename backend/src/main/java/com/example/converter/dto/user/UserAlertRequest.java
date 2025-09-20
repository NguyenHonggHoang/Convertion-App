package com.example.converter.dto.user;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAlertRequest {
    @NotBlank
    private String baseCurrency;
    @NotBlank
    private String targetCurrency;
    @NotBlank
    private String alertType;
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal thresholdRate;
}
