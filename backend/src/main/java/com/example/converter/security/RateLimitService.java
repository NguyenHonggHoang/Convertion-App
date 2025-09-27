package com.example.converter.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to track login and registration attempts per IP address
 * and determine if reCAPTCHA is required based on thresholds.
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
        public long firstAttemptEpochSec = System.currentTimeMillis()/1000;
        public long lastAttemptEpochSec  = System.currentTimeMillis()/1000;

        public boolean isExpired() {
            long now = System.currentTimeMillis()/1000;
            return lastAttemptEpochSec < now - WINDOW_MINUTES * 60L;
        }

        public void recordAttempt() {
            count.incrementAndGet();
            lastAttemptEpochSec = System.currentTimeMillis()/1000;
        }

        public void reset() {
            count.set(0);
            long now = System.currentTimeMillis()/1000;
            firstAttemptEpochSec = now;
            lastAttemptEpochSec  = now;
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

    // Cleanup
    @Scheduled(fixedDelay = 300000)
    public void purgeExpired() {
        purgeMap(loginAttempts, "login");
        purgeMap(registrationAttempts, "registration");
    }

    private void purgeMap(Map<String, AttemptInfo> map, String action) {
        int before = map.size();
        map.entrySet().removeIf(e -> e.getValue().isExpired());
        int after = map.size();
        if (after != before) {
            log.info("Purged {} expired {} entries: {} -> {}", (before-after), action, before, after);
        }
    }

}