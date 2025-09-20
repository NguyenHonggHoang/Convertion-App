package com.example.converter.controller.internal;

import com.example.converter.security.InternalApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Internal controller for service-to-service operations like token generation.
 * This endpoint should only be accessible internally within the Docker network.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalController {

    private final InternalApiKeyService internalApiKeyService;
    
    @Value("${security.internal.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    /**
     * Generate internal API key for microservices
     * This endpoint allows services to obtain signed tokens for API access
     */
    @PostMapping("/token")
    public ResponseEntity<?> generateInternalToken(
        @RequestParam String serviceName,
        @RequestParam(defaultValue = "60") int durationMinutes,
        HttpServletRequest request
    ) {
        try {
            // Basic security - only allow from internal network
            String remoteAddr = request.getRemoteAddr();
            if (!isInternalRequest(remoteAddr)) {
                log.warn("Token generation attempt from external address: {}", remoteAddr);
                return ResponseEntity.status(403).body(Map.of("error", "forbidden", "message", "External access not allowed"));
            }

            if (serviceName == null || serviceName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_service", "message", "Service name is required"));
            }

            if (durationMinutes < 1 || durationMinutes > 1440) { // Max 24 hours
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_duration", "message", "Duration must be between 1 and 1440 minutes"));
            }

            String token = internalApiKeyService.generateInternalApiKey(serviceName, durationMinutes);
            
            log.info("Generated internal API token for service: {} (duration: {} minutes)", serviceName, durationMinutes);
            
            // Get expiration time safely
            LocalDateTime expirationTime = null;
            try {
                expirationTime = internalApiKeyService.getTokenExpiration(token);
            } catch (Exception e) {
                log.warn("Could not extract expiration from token, calculating manually", e);
                expirationTime = LocalDateTime.now().plusMinutes(durationMinutes);
            }

            return ResponseEntity.ok(Map.of(
                "token", token,
                "service", serviceName,
                "duration_minutes", durationMinutes,
                "expires_at", expirationTime != null ? expirationTime.toString() : "unknown"
            ));
            
        } catch (Exception e) {
            log.error("Error generating internal API token for service: {}", serviceName, e);
            return ResponseEntity.status(500).body(Map.of("error", "generation_failed", "message", "Failed to generate internal API token"));
        }
    }

    /**
     * Validate an internal API token
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateInternalToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", "Token is required"));
            }

            boolean isValid = internalApiKeyService.validateInternalApiKey(token);
            String serviceName = internalApiKeyService.getServiceNameFromToken(token);
            
            return ResponseEntity.ok(Map.of(
                "valid", isValid,
                "service", serviceName != null ? serviceName : "unknown",
                "expires_at", internalApiKeyService.getTokenExpiration(token)
            ));
            
        } catch (Exception e) {
            log.error("Error validating internal API token", e);
            return ResponseEntity.status(500).body(Map.of("error", "validation_failed", "message", "Failed to validate token"));
        }
    }

    /**
     * Check if request comes from internal network (Docker network or localhost)
     */
    private boolean isInternalRequest(String remoteAddr) {
        if (remoteAddr == null) return false;
        
        // Allow localhost and Docker internal networks
        return remoteAddr.equals("127.0.0.1") || 
               remoteAddr.equals("::1") || 
               remoteAddr.equals("0:0:0:0:0:0:0:1") ||
               remoteAddr.startsWith("172.") || // Docker default networks
               remoteAddr.startsWith("192.168.") ||
               remoteAddr.startsWith("10.") ||
               remoteAddr.equals("::ffff:127.0.0.1");
    }
}