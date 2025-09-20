package com.example.converter.controller.user;

import com.example.converter.dto.user.AuthResponse;
import com.example.converter.dto.user.UserLoginRequest;
import com.example.converter.dto.user.UserRegistrationRequest;
import com.example.converter.entity.User;
import com.example.converter.service.user.UserService;
import com.example.converter.domain.service.CaptchaService;
import com.example.converter.security.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// bổ sung import cho logout
import com.example.converter.security.jwt.JwtIssuerService;
import com.example.converter.security.jwt.TokenBlacklistService;
import com.example.converter.domain.service.RefreshTokenService;

/**
 * Spring Boot REST Controller for user management functionality.
 * Endpoints: /api/auth/register (POST), /api/auth/login (POST), /api/users/me (GET), /api/users/me (PUT).
 * Uses UserService to handle business logic.
 * Ensures endpoints /api/users/** are protected by JWT.
 * Includes Swagger annotations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for user registration, authentication, and profile management")
public class UserController {

    private final UserService userService;
    private final CaptchaService captchaService;
    private final RateLimitService rateLimitService;

    private final JwtIssuerService jwtIssuerService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/api/auth/register")
    @Operation(summary = "Register user", description = "Register a new user account")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRegistrationRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
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
            setRefreshCookie(httpResponse, response.getRefreshToken());

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

    @PostMapping("/api/auth/login")
    @Operation(summary = "Login user", description = "Authenticate user and return JWT token")
    public ResponseEntity<?> loginUser(@Valid @RequestBody UserLoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
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
            setRefreshCookie(httpResponse, response.getRefreshToken());

            // Reset attempts on successful login
            rateLimitService.resetLoginAttempts(clientIP);
            log.info("Login successful for user: {} from IP: {}", request.getUsername(), clientIP);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Login failed for user: {} from IP: {}", request.getUsername(), clientIP, e);

            // Record failed attempt for rate limiting
            rateLimitService.recordFailedLoginAttempt(clientIP);

            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record RefreshReq(String refresh_token, String deviceId) {}

    @PostMapping("/api/auth/refresh")
    @Operation(summary = "Refresh token", description = "returnnn new JWT token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody(required = false) RefreshReq req,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {

        try {
            String token = (req != null && req.refresh_token() != null && !req.refresh_token().isBlank())
                    ? req.refresh_token()
                    : null;
            if (token == null && request.getCookies() != null) {
                for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                    if ("refresh_token".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                        token = c.getValue();
                        break;
                    }
                }
            }
            // Blacklist the presented ACCESS token if provided with refresh request
            try {
                String candidate = null;
                String bearer = request.getHeader("Authorization");
                if (bearer != null && bearer.startsWith("Bearer ")) {
                    String tok = bearer.substring(7);
                    if (jwtIssuerService.validateToken(tok)) {
                        Map<String, Object> c = jwtIssuerService.getClaimsFromToken(tok);
                        if ("access".equals(c.get("token_type"))) candidate = tok;
                    }
                }
                if (candidate == null) {
                    String xAccess = request.getHeader("X-Access-Token");
                    if (xAccess != null && !xAccess.isBlank() && jwtIssuerService.validateToken(xAccess)) {
                        Map<String, Object> c = jwtIssuerService.getClaimsFromToken(xAccess);
                        if ("access".equals(c.get("token_type"))) candidate = xAccess;
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
        AuthResponse authResponse = (token != null)
            ? userService.refreshToken(token, request)
            : userService.refreshToken(request);
            setRefreshCookie(response, authResponse.getRefreshToken());
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/api/auth/logout")
    @Operation(summary = "Logout current session")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                    HttpServletResponse resp) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "missing_token"));
            }

            String token = authHeader.substring(7);
            if (!jwtIssuerService.validateToken(token)) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_token"));
            }

            Map<String, Object> claims = jwtIssuerService.getClaimsFromToken(token);
            String jti = (String) claims.get("jti");
            Number expNum = (Number) claims.get("exp");
            if (jti != null && expNum != null) {
                long exp = expNum.longValue();
                long now = System.currentTimeMillis() / 1000;
                long ttlSeconds = Math.max(0, exp - now);
                tokenBlacklistService.blacklist(jti, ttlSeconds);
                log.info("Token with JTI {} has been blacklisted", jti);
            }

            clearRefreshCookie(resp);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            log.error("Logout failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "logout_failed"));
        }
    }

    @PostMapping("/api/auth/logout-all")
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

    @GetMapping("/api/users/me")
    @Operation(summary = "Get user profile", description = "Get current user's profile information")
    public ResponseEntity<User> getUserProfile() {
        try {
            Long userId = getCurrentUserId();
            User user = userService.getUserProfile(userId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            log.error("Failed to get user profile: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/api/users/me")
    @Operation(summary = "Update user profile", description = "Update current user's profile information")
    public ResponseEntity<User> updateUserProfile(@RequestParam(required = false) String email,
                                                @RequestParam(required = false) String fullName) {
        try {
            Long userId = getCurrentUserId();
            User updatedUser = userService.updateUserProfile(userId, email, fullName);
            log.info("User profile updated successfully for user ID: {}", userId);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            log.error("Failed to update user profile: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get current user ID from authentication context
     * @return current user ID
     */
    //Dev environment
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return 1L;
    }

    /**
     * Extract client IP address from request
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

    private void setRefreshCookie(HttpServletResponse resp, String token) {
        if (token == null || token.isBlank()) return;
        try {
            // We don't have JwtIssuerService here, so just set a default max-age; browser will replace on rotation
            jakarta.servlet.http.Cookie ck = new jakarta.servlet.http.Cookie("refresh_token", token);
            ck.setHttpOnly(true);
            ck.setPath("/");
            ck.setMaxAge(30 * 24 * 3600);
            resp.addCookie(ck);
            resp.addHeader("Set-Cookie", "refresh_token=" + token + "; Max-Age=" + (30*24*3600) + "; Path=/; HttpOnly; SameSite=Lax");
        } catch (RuntimeException ex) {
            log.warn("Failed to set refresh cookie: {}", ex.getMessage());
        }
    }

    private void clearRefreshCookie(HttpServletResponse resp) {
        jakarta.servlet.http.Cookie ck = new jakarta.servlet.http.Cookie("refresh_token", "");
        ck.setHttpOnly(true);
        ck.setPath("/");
        ck.setMaxAge(0);
        resp.addCookie(ck);
        resp.addHeader("Set-Cookie", "refresh_token=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax");
    }
}
