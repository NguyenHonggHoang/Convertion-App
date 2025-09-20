package com.example.converter.dto.user;

import com.example.converter.entity.UserAlertConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAlertResponse {
    private Long id;
    private String baseCurrency;
    private String targetCurrency;
    private String alertType;
    private BigDecimal thresholdRate;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static UserAlertResponse fromEntity(UserAlertConfig config) {
        return new UserAlertResponse(
                config.getId(),
                config.getBaseCurrency(),
                config.getTargetCurrency(),
                config.getAlertType(),
                config.getThresholdRate(),
                config.getIsActive(),
                config.getCreatedAt()
        );
    }
}
