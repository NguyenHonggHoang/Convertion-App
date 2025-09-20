package com.example.converter.web.controller;

import com.example.converter.domain.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Profile("dev")
public class CaptchaTestController {

    private final CaptchaService captchaService;

    @PostMapping("/test")
    public ResponseEntity<?> testCaptcha(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String token = request.get("captchaToken");
            String remoteIp = getClientIP(httpRequest);

            log.info("Testing captcha - token: {}, remoteIp: {}",
                    token != null ? token.substring(0, Math.min(token.length(), 20)) + "..." : "null",
                    remoteIp);

            boolean isValid = captchaService.verify(token, remoteIp);

            if (isValid) {
                log.info("Captcha verification successful");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Captcha verification successful"
                ));
            } else {
                log.warn("Captcha verification failed");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Captcha verification failed"
                ));
            }
        } catch (Exception e) {
            log.error("Error verifying captcha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Server error during captcha verification"
            ));
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "X-Originating-IP",
                "CF-Connecting-IP",
                "True-Client-IP"
        };

        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}