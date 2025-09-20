package com.example.converter.security;

import com.example.converter.security.jwt.JwtIssuerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for generating and validating internal API keys using Nimbus-based JWT signing.
 * Used for secure service-to-service communication within the microservices architecture.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InternalApiKeyService {

    private final JwtIssuerService issuer;

    /**
     * Generate a signed internal API key for a specific service
     * @param serviceName Name of the service requesting the token
     * @param durationMinutes Token validity duration in minutes (default 60 minutes)
     * @return Signed JWT token for internal API access
     */
    public String generateInternalApiKey(String serviceName, int durationMinutes) {
        Map<String,Object> extra = new HashMap<>();
        extra.put("service", serviceName);
        extra.put("scope", "internal-api");
        extra.put("type", "service-token");
        long ttlSec = durationMinutes * 60L;
        return issuer.issueToken(serviceName, List.of("INTERNAL"), null, ttlSec, extra);
    }

    /**
     * Generate a signed internal API key with default 60-minute expiration
     * @param serviceName Name of the service requesting the token
     * @return Signed JWT token for internal API access
     */
    public String generateInternalApiKey(String serviceName) {
        return generateInternalApiKey(serviceName, 60);
    }

    /**
     * Validate an internal API key and extract service information
     * @param token JWT token to validate
     * @return True if token is valid and authorized for internal API access
     */
    public boolean validateInternalApiKey(String token) {
        try {
            // Use JwtIssuerService for validation instead of JwtValidator to avoid remote JWKS dependency
            if (!issuer.validateToken(token)) {
                return false;
            }

            Map<String, Object> claims = issuer.getClaimsFromToken(token);
            String scope = (String) claims.get("scope");
            String type = (String) claims.get("type");
            boolean ok = "internal-api".equals(scope) && "service-token".equals(type);
            if (ok) {
                log.debug("Validated internal API key for service: {}", claims.get("sub"));
            }
            return ok;
        } catch (Exception e) {
            log.warn("Invalid internal API key: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract service name from a validated internal API key
     * @param token JWT token to extract service name from
     * @return Service name or null if token is invalid
     */
    public String getServiceNameFromToken(String token) {
        try {
            Map<String, Object> claims = issuer.getClaimsFromToken(token);
            String svc = (String) claims.get("service");
            return svc != null ? svc : (String) claims.get("sub");
        } catch (Exception e) {
            log.warn("Error extracting service name from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get token expiration time
     * @param token JWT token to check expiration
     * @return Expiration time as LocalDateTime or null if token is invalid
     */
    public LocalDateTime getTokenExpiration(String token) {
        try {
            Map<String, Object> claims = issuer.getClaimsFromToken(token);
            Object expObj = claims.get("exp");
            if (expObj instanceof Number) {
                long expSeconds = ((Number) expObj).longValue();
                return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(expSeconds), ZoneId.systemDefault());
            }
            return null;
        } catch (Exception e) {
            log.warn("Error extracting expiration from token: {}", e.getMessage());
            return null;
        }
    }
}