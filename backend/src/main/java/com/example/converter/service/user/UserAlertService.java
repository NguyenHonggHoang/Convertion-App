package com.example.converter.service.user;

import com.example.converter.dto.user.UserAlertRequest;
import com.example.converter.dto.user.UserAlertResponse;
import com.example.converter.entity.UserAlertConfig;
import com.example.converter.repository.UserAlertConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAlertService {

    private final UserAlertConfigRepository userAlertConfigRepository;

    public UserAlertResponse createAlert(Long userId, UserAlertRequest request) {
        UserAlertConfig config = new UserAlertConfig();
        config.setUserId(userId);
        config.setBaseCurrency(request.getBaseCurrency());
        config.setTargetCurrency(request.getTargetCurrency());
        config.setAlertType(request.getAlertType());
        config.setThresholdRate(request.getThresholdRate());
        config.setIsActive(true);
        UserAlertConfig saved = userAlertConfigRepository.save(config);
        return UserAlertResponse.fromEntity(saved);
    }

    public UserAlertResponse updateAlert(Long id, Long userId, UserAlertRequest request) {
        UserAlertConfig config = userAlertConfigRepository.findById(id)
                .filter(cfg -> cfg.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        config.setBaseCurrency(request.getBaseCurrency());
        config.setTargetCurrency(request.getTargetCurrency());
        config.setAlertType(request.getAlertType());
        config.setThresholdRate(request.getThresholdRate());
        UserAlertConfig updated = userAlertConfigRepository.save(config);
        return UserAlertResponse.fromEntity(updated);
    }

    public void deleteAlert(Long id, Long userId) {
        UserAlertConfig config = userAlertConfigRepository.findById(id)
                .filter(cfg -> cfg.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        userAlertConfigRepository.delete(config);
    }
}
