package com.example.converter.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to manage rate limiting and determine when reCAPTCHA is required
 */
@Service
@Slf4j
public class RateLimitService {

    private final Map<String, AttemptInfo> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, AttemptInfo> registrationAttempts = new ConcurrentHashMap<>();

    // Configuration
    private static final int MAX_ATTEMPTS_BEFORE_CAPTCHA = 3;
    private static final int WINDOW_MINUTES = 15;

    public static class AttemptInfo {
        public final AtomicInteger count = new AtomicInteger(0);
        public LocalDateTime firstAttempt = LocalDateTime.now();
        public LocalDateTime lastAttempt = LocalDateTime.now();

        public boolean isExpired() {
            return lastAttempt.isBefore(LocalDateTime.now().minusMinutes(WINDOW_MINUTES));
        }

        public void recordAttempt() {
            count.incrementAndGet();
            lastAttempt = LocalDateTime.now();
        }

        public void reset() {
            count.set(0);
            firstAttempt = LocalDateTime.now();
            lastAttempt = LocalDateTime.now();
        }
    }

    /**
     * Check if reCAPTCHA is required for login attempts from this IP
     */
    public boolean isCaptchaRequiredForLogin(String clientIP) {
        return isCaptchaRequired(loginAttempts, clientIP, "login");
    }

    /**
     * Check if reCAPTCHA is required for registration attempts from this IP
     */
    public boolean isCaptchaRequiredForRegistration(String clientIP) {
        return isCaptchaRequired(registrationAttempts, clientIP, "registration");
    }

    /**
     * Record a failed login attempt
     */
    public void recordFailedLoginAttempt(String clientIP) {
        recordFailedAttempt(loginAttempts, clientIP, "login");
    }

    /**
     * Record a failed registration attempt
     */
    public void recordFailedRegistrationAttempt(String clientIP) {
        recordFailedAttempt(registrationAttempts, clientIP, "registration");
    }

    /**
     * Reset attempts for successful login
     */
    public void resetLoginAttempts(String clientIP) {
        loginAttempts.remove(clientIP);
        log.info("Reset login attempts for IP: {}", clientIP);
    }

    /**
     * Reset attempts for successful registration
     */
    public void resetRegistrationAttempts(String clientIP) {
        registrationAttempts.remove(clientIP);
        log.info("Reset registration attempts for IP: {}", clientIP);
    }

    private boolean isCaptchaRequired(Map<String, AttemptInfo> attempts, String clientIP, String action) {
        AttemptInfo info = attempts.get(clientIP);
        if (info == null) {
            return false;
        }

        if (info.isExpired()) {
            attempts.remove(clientIP);
            return false;
        }

        boolean required = info.count.get() >= MAX_ATTEMPTS_BEFORE_CAPTCHA;
        log.info("reCAPTCHA required for {} from IP {}: {} (attempts: {})",
                action, clientIP, required, info.count.get());

        return required;
    }

    private void recordFailedAttempt(Map<String, AttemptInfo> attempts, String clientIP, String action) {
        attempts.compute(clientIP, (ip, info) -> {
            if (info == null || info.isExpired()) {
                info = new AttemptInfo();
            }
            info.recordAttempt();
            log.info("Recorded failed {} attempt for IP {}: {} attempts in current window",
                    action, ip, info.count.get());
            return info;
        });
    }

    /**
     * Get current attempt count for debugging
     */
    public int getLoginAttemptCount(String clientIP) {
        AttemptInfo info = loginAttempts.get(clientIP);
        return (info != null && !info.isExpired()) ? info.count.get() : 0;
    }

    /**
     * Get current attempt count for debugging
     */
    public int getRegistrationAttemptCount(String clientIP) {
        AttemptInfo info = registrationAttempts.get(clientIP);
        return (info != null && !info.isExpired()) ? info.count.get() : 0;
    }
}