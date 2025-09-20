package com.example.converter.web.controller;

import com.example.converter.dto.user.AuthResponse;
import com.example.converter.dto.user.UserLoginRequest;
import com.example.converter.dto.user.UserRegistrationRequest;
import com.example.converter.service.user.UserService;
import com.example.converter.security.jwt.JwtIssuerService;
import com.example.converter.security.jwt.TokenBlacklistService;
import com.example.converter.domain.service.RefreshTokenService;
import com.example.converter.domain.service.CaptchaService;
import com.example.converter.security.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final JwtIssuerService jwtIssuerService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final CaptchaService captchaService;
    private final RateLimitService rateLimitService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registration successful"),
            @ApiResponse(responseCode = "400", description = "Registration failed"),
            @ApiResponse(responseCode = "429", description = "reCAPTCHA required")
    })
    public ResponseEntity<?> register(@Valid @RequestBody UserRegistrationRequest request, HttpServletRequest httpRequest) {
        String clientIP = getClientIP(httpRequest);
        log.info("Registration attempt for user: {} from IP: {}", request.getUsername(), clientIP);

        try {
            // Check if reCAPTCHA is required
            boolean captchaRequired = rateLimitService.isCaptchaRequiredForRegistration(clientIP);

            if (captchaRequired) {
                log.info("reCAPTCHA required for registration from IP: {}", clientIP);

                // Verify reCAPTCHA if provided
                if (request.getCaptchaToken() == null || request.getCaptchaToken().isBlank()) {
                    return ResponseEntity.status(429).body(Map.of(
                            "error", "captcha_required",
                            "message", "reCAPTCHA verification required due to multiple failed attempts"
                    ));
                }

                boolean captchaValid = captchaService.verify(request.getCaptchaToken(), clientIP);
                if (!captchaValid) {
                    log.warn("Invalid reCAPTCHA for registration from IP: {}", clientIP);
                    rateLimitService.recordFailedRegistrationAttempt(clientIP);
                    return ResponseEntity.status(429).body(Map.of(
                            "error", "invalid_captcha",
                            "message", "reCAPTCHA verification failed"
                    ));
                }
                log.info("reCAPTCHA verified successfully for registration from IP: {}", clientIP);
            }

            AuthResponse response = userService.registerUser(request, httpRequest);

            // Reset attempts on successful registration
            rateLimitService.resetRegistrationAttempts(clientIP);
            log.info("Registration successful for user: {} from IP: {}", request.getUsername(), clientIP);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Registration failed for user: {} from IP: {}", request.getUsername(), clientIP, e);

            // Record failed attempt for rate limiting
            rateLimitService.recordFailedRegistrationAttempt(clientIP);

            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "User login")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "429", description = "reCAPTCHA required")
    })
    public ResponseEntity<?> login(@Valid @RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
        String clientIP = getClientIP(httpRequest);
        log.info("Login attempt for user: {} from IP: {}", request.getUsername(), clientIP);

        try {
            // Check if reCAPTCHA is required
            boolean captchaRequired = rateLimitService.isCaptchaRequiredForLogin(clientIP);

            if (captchaRequired) {
                log.info("reCAPTCHA required for login from IP: {}", clientIP);

                // Verify reCAPTCHA if provided
                if (request.getCaptchaToken() == null || request.getCaptchaToken().isBlank()) {
                    return ResponseEntity.status(429).body(Map.of(
                            "error", "captcha_required",
                            "message", "reCAPTCHA verification required due to multiple failed attempts"
                    ));
                }

                boolean captchaValid = captchaService.verify(request.getCaptchaToken(), clientIP);
                if (!captchaValid) {
                    log.warn("Invalid reCAPTCHA for login from IP: {}", clientIP);
                    rateLimitService.recordFailedLoginAttempt(clientIP);
                    return ResponseEntity.status(429).body(Map.of(
                            "error", "invalid_captcha",
                            "message", "reCAPTCHA verification failed"
                    ));
                }
                log.info("reCAPTCHA verified successfully for login from IP: {}", clientIP);
            }

            AuthResponse response = userService.authenticateUser(request, httpRequest);

            // Reset attempts on successful login
            rateLimitService.resetLoginAttempts(clientIP);
            log.info("Login successful for user: {} from IP: {}", request.getUsername(), clientIP);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Login failed for user: {} from IP: {}", request.getUsername(), clientIP, e);

            // Record failed attempt for rate limiting
            rateLimitService.recordFailedLoginAttempt(clientIP);

            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    public record RefreshReq(String refresh_token, String deviceId) {}

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(mediaType = "application/json",
                            examples = {@ExampleObject(name = "TokenResponse",
                                    value = "{\"access_token\":\"<JWT>\",\"refresh_token\":\"<new_jti>\"}")})),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    })
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshReq req, HttpServletRequest http) {
        try {
            // Prefer token from body; fallback to Authorization header
            String token = req != null && req.refresh_token() != null && !req.refresh_token().isBlank()
                    ? req.refresh_token()
                    : null;
            // Blacklist current ACCESS token if client sent it along
            try {
                String candidate = null;
                String bearer = http.getHeader("Authorization");
                if (bearer != null && bearer.startsWith("Bearer ")) {
                    String tok = bearer.substring(7);
                    if (jwtIssuerService.validateToken(tok)) {
                        Map<String, Object> claims = jwtIssuerService.getClaimsFromToken(tok);
                        if ("access".equals(claims.get("token_type"))) {
                            candidate = tok;
                        }
                    }
                }
                if (candidate == null) {
                    String xAccess = http.getHeader("X-Access-Token");
                    if (xAccess != null && !xAccess.isBlank() && jwtIssuerService.validateToken(xAccess)) {
                        Map<String, Object> claims = jwtIssuerService.getClaimsFromToken(xAccess);
                        if ("access".equals(claims.get("token_type"))) {
                            candidate = xAccess;
                        }
                    }
                }
                if (candidate != null) {
                    Map<String, Object> aClaims = jwtIssuerService.getClaimsFromToken(candidate);
                    String aJti = (String) aClaims.get("jti");
                    Number aExp = (Number) aClaims.get("exp");
                    if (aJti != null && aExp != null) {
                        long now = System.currentTimeMillis() / 1000L;
                        long ttl = Math.max(0, aExp.longValue() - now);
                        tokenBlacklistService.blacklist(aJti, ttl);
                        log.info("ACCESS token blacklisted during refresh, jti={} ttl={}s", aJti, ttl);
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not blacklist ACCESS token on refresh: {}", ex.getMessage());
            }
        AuthResponse response = (token != null)
            ? userService.refreshToken(token, http)
            : userService.refreshToken(http);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return ResponseEntity.status(401).body(Map.of("error", "invalid_refresh"));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current session")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "missing_token"));
            }

            // Extract token and validate
            String token = authHeader.substring(7);

            // Validate token first
            if (!jwtIssuerService.validateToken(token)) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_token"));
            }

            // Parse token để lấy JTI và expiration time
            Map<String, Object> claims = jwtIssuerService.getClaimsFromToken(token);
            String jti = (String) claims.get("jti");
            Long exp = (Long) claims.get("exp");

            if (jti != null && exp != null) {
                // Tính toán thời gian TTL còn lại (exp là timestamp Unix)
                long currentTime = System.currentTimeMillis() / 1000;
                long ttlSeconds = Math.max(0, exp - currentTime);

                // Blacklist token với TTL còn lại
                tokenBlacklistService.blacklist(jti, ttlSeconds);
                log.info("Token with JTI {} has been blacklisted", jti);
            }

            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            log.error("Logout failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "logout_failed"));
        }
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke all sessions for current user")
    public ResponseEntity<?> logoutAll(@RequestHeader(value = "X-User", required = false) String user) {
        if (user == null || user.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_user"));
        }

        try {
            refreshTokenService.revokeAll(user);
            return ResponseEntity.ok(Map.of("message", "All sessions revoked"));
        } catch (Exception e) {
            log.error("Logout all failed for user: {}", user, e);
            return ResponseEntity.status(500).body(Map.of("error", "logout_all_failed"));
        }
    }

    /**
     * Extract client IP address from HTTP request
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }
}
