package com.example.converter.service.user;

import com.example.converter.dto.user.AuthResponse;
import com.example.converter.dto.user.UserLoginRequest;
import com.example.converter.dto.user.UserRegistrationRequest;
import com.example.converter.entity.User;
import com.example.converter.repository.UserRepository;
import com.example.converter.security.jwt.JwtIssuerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.converter.domain.service.RefreshTokenService;
import com.example.converter.security.jwt.TokenBlacklistService;
import com.example.converter.domain.service.AccessTokenService;

/**
 * Spring Boot Service for user management functionality.
 * Methods: registerUser(UserRegistrationRequest request), authenticateUser(UserLoginRequest request), getUserProfile(Long userId), updateUserProfile(Long userId, UserProfileUpdateRequest request).
 * Logic:
 *   1. Registration: Encode password, save to UserRepository.
 *   2. Login: Authenticate login information, create and return JWT.
 *   3. Get/update profile: Interact with UserRepository.
 * Uses Spring Security and JWT.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtIssuerService jwtIssuerService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AccessTokenService accessTokenService;

    private List<String> extractRoles(UserDetails userDetails) {
        if (userDetails == null || userDetails.getAuthorities() == null) return List.of();
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    /**
     * Register a new user
     * @param request the registration request
     * @return AuthResponse with JWT token
     */
    public AuthResponse registerUser(UserRegistrationRequest request, HttpServletRequest httpRequest) {
        log.info("Registering new user: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        // Authenticate ngay sau khi đăng ký
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        List<String> roles = extractRoles(principal);

        String deviceId = resolveDeviceId(httpRequest);

        // Tạo access và refresh token
        String accessToken = jwtIssuerService.issueAccessToken(String.valueOf(savedUser.getId()), roles, deviceId);
        String refreshToken = jwtIssuerService.issueRefreshToken(String.valueOf(savedUser.getId()), deviceId);

        try {
            Map<String, Object> rClaims = jwtIssuerService.getClaimsFromToken(refreshToken);
            String sub = (String) rClaims.get("sub");
            String jti = (String) rClaims.get("jti");
            Number expNum = (Number) rClaims.get("exp");
            if (sub != null && jti != null && expNum != null) {
                long now = System.currentTimeMillis() / 1000L;
                long ttl = Math.max(1, expNum.longValue() - now);
                refreshTokenService.allow(sub, jti, ttl);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to allow initial refresh token: {}", e.getMessage());
        }

        return new AuthResponse(accessToken, refreshToken);
    }

    /**
     * Authenticate user login
     * @param request the login request
     * @return AuthResponse with JWT token
     */
    public AuthResponse authenticateUser(UserLoginRequest request, HttpServletRequest httpRequest) {
        log.info("Authenticating user: {}", request.getUsername());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Cập nhật thời gian đăng nhập cuối
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            List<String> roles = extractRoles(userDetails);
            String deviceId = resolveDeviceId(httpRequest);
            String accessToken = jwtIssuerService.issueAccessToken(String.valueOf(user.getId()), roles, deviceId);
            String refreshToken = jwtIssuerService.issueRefreshToken(String.valueOf(user.getId()), deviceId);

            // Allow refresh token hiện hành cho user
            try {
                Map<String, Object> rClaims = jwtIssuerService.getClaimsFromToken(refreshToken);
                String sub = (String) rClaims.get("sub");
                String jti = (String) rClaims.get("jti");
                Number expNum = (Number) rClaims.get("exp");
                if (sub != null && jti != null && expNum != null) {
                    long now = System.currentTimeMillis() / 1000L;
                    long ttl = Math.max(1, expNum.longValue() - now);
                    refreshTokenService.allow(sub, jti, ttl);
                }
            } catch (RuntimeException e) {
                log.warn("Failed to allow login refresh token: {}", e.getMessage());
            }

            return new AuthResponse(accessToken, refreshToken);

        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.getUsername(), e);
            throw new RuntimeException("Invalid username or password");
        }
    }

    /**
     * Refresh access token using refresh token string
     */
    public AuthResponse refreshToken(String token, HttpServletRequest httpRequest) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Missing or invalid refresh token");
        }

        // Validate refresh token
        if (!jwtIssuerService.validateToken(token)) {
            throw new RuntimeException("Invalid refresh token");
        }

        // Parse claims từ token
        Map<String, Object> claims = jwtIssuerService.getClaimsFromToken(token);
        String tokenType = (String) claims.get("token_type");
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Not a refresh token");
        }

        // Lấy subject và JTI để kiểm tra allow/blacklist
        String sub = (String) claims.get("sub");
        String oldJti = (String) claims.get("jti");
        Number expNum = (Number) claims.get("exp");
        if (sub == null || oldJti == null || expNum == null) {
            throw new RuntimeException("Malformed refresh token");
        }

        long now = System.currentTimeMillis() / 1000L;
        long ttlRemain = Math.max(1, expNum.longValue() - now);

        // Chỉ cho phép refresh nếu refresh token hiện hành được allow và không bị blacklist
        if (!refreshTokenService.isAllowed(sub, oldJti)) {
            throw new RuntimeException("Refresh token not allowed (rotated or revoked)");
        }
        if (refreshTokenService.isBlacklisted(oldJti)) {
            throw new RuntimeException("Refresh token revoked");
        }

        // Blacklist access token cũ nếu client cung cấp qua header X-Access-Token; nếu không có thì revoke theo epoch
        try {
            String oldAccessHeader = httpRequest.getHeader("X-Access-Token");
            if (oldAccessHeader != null && !oldAccessHeader.isBlank()) {
                String oldAccessToken = oldAccessHeader.startsWith("Bearer ") ? oldAccessHeader.substring(7) : oldAccessHeader;
                if (jwtIssuerService.validateToken(oldAccessToken)) {
                    Map<String, Object> aClaims = jwtIssuerService.getClaimsFromToken(oldAccessToken);
                    if ("access".equals(aClaims.get("token_type"))) {
                        String aJti = (String) aClaims.get("jti");
                        Number aExp = (Number) aClaims.get("exp");
                        if (aJti != null && aExp != null && aExp.longValue() > now) {
                            long aTtl = aExp.longValue() - now;
                            tokenBlacklistService.blacklist(aJti, aTtl);
                            log.info("Old access token blacklisted by JTI: {}", aJti);
                        }
                    }
                }
            } else {
                // Không có access token cũ, revoke tất cả access token phát hành trước thời điểm hiện tại
                accessTokenService.revokeAllSinceNow(sub);
                log.info("Revoked all previous access tokens by epoch for sub: {}", sub);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist old access token or revoke by epoch: {}", e.getMessage());
        }

        // Tìm user và tạo token mới
        long userId;
        try {
            userId = Long.parseLong(sub);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid subject in token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserDetails userDetails = loadUserByUsername(user.getUsername());
        List<String> roles = extractRoles(userDetails);

        // Tạo access và refresh token mới
        String newAccessToken = jwtIssuerService.issueAccessToken(String.valueOf(user.getId()), roles, null);
        String newRefreshToken = jwtIssuerService.issueRefreshToken(String.valueOf(user.getId()), null);

        // Rotate: allow JTI mới, blacklist JTI cũ đến khi hết hạn
        try {
            Map<String, Object> newClaims = jwtIssuerService.getClaimsFromToken(newRefreshToken);
            String newJti = (String) newClaims.get("jti");
            Number newExpNum = (Number) newClaims.get("exp");
            if (newJti != null && newExpNum != null) {
                long newTtl = Math.max(1, newExpNum.longValue() - now);
                refreshTokenService.allow(sub, newJti, newTtl);
            }
            // Blacklist JTI cũ để tránh replay đến khi hết hạn
            refreshTokenService.blacklist(oldJti, ttlRemain);
        } catch (RuntimeException e) {
            log.warn("Refresh rotation housekeeping failed: {}", e.getMessage());
        }

        log.info("Token refreshed successfully for user: {}", user.getUsername());
        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    /**
     * Refresh access token using refresh token from Authorization header
     */
    public AuthResponse refreshToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid refresh token");
        }

        String token = authHeader.substring(7);

        // Validate refresh token
        if (!jwtIssuerService.validateToken(token)) {
            throw new RuntimeException("Invalid refresh token");
        }

        // Parse claims từ token
        Map<String, Object> claims = jwtIssuerService.getClaimsFromToken(token);
        String tokenType = (String) claims.get("token_type");
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Not a refresh token");
        }

        // Lấy subject và JTI để kiểm tra allow/blacklist
        String sub = (String) claims.get("sub");
        String oldJti = (String) claims.get("jti");
        Number expNum = (Number) claims.get("exp");
        if (sub == null || oldJti == null || expNum == null) {
            throw new RuntimeException("Malformed refresh token");
        }

        long now = System.currentTimeMillis() / 1000L;
        long ttlRemain = Math.max(1, expNum.longValue() - now);

        // Chỉ cho phép refresh nếu refresh token hiện hành được allow và không bị blacklist
        if (!refreshTokenService.isAllowed(sub, oldJti)) {
            throw new RuntimeException("Refresh token not allowed (rotated or revoked)");
        }
        if (refreshTokenService.isBlacklisted(oldJti)) {
            throw new RuntimeException("Refresh token revoked");
        }

        // Blacklist access token cũ nếu client cung cấp qua header X-Access-Token; nếu không có thì revoke theo epoch
        try {
            String oldAccessHeader = request.getHeader("X-Access-Token");
            if (oldAccessHeader != null && !oldAccessHeader.isBlank()) {
                String oldAccessToken = oldAccessHeader.startsWith("Bearer ") ? oldAccessHeader.substring(7) : oldAccessHeader;
                if (jwtIssuerService.validateToken(oldAccessToken)) {
                    Map<String, Object> aClaims = jwtIssuerService.getClaimsFromToken(oldAccessToken);
                    if ("access".equals(aClaims.get("token_type"))) {
                        String aJti = (String) aClaims.get("jti");
                        Number aExp = (Number) aClaims.get("exp");
                        if (aJti != null && aExp != null && aExp.longValue() > now) {
                            long aTtl = aExp.longValue() - now;
                            tokenBlacklistService.blacklist(aJti, aTtl);
                            log.info("Old access token blacklisted by JTI: {}", aJti);
                        }
                    }
                }
            } else {
                // Không có access token cũ, revoke tất cả access token phát hành trước thời điểm hiện tại
                accessTokenService.revokeAllSinceNow(sub);
                log.info("Revoked all previous access tokens by epoch for sub: {}", sub);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist old access token or revoke by epoch: {}", e.getMessage());
        }

        // Tìm user và tạo token mới
        long userId;
        try {
            userId = Long.parseLong(sub);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid subject in token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserDetails userDetails = loadUserByUsername(user.getUsername());
        List<String> roles = extractRoles(userDetails);

        // Tạo access và refresh token mới
        String newAccessToken = jwtIssuerService.issueAccessToken(String.valueOf(user.getId()), roles, null);
        String newRefreshToken = jwtIssuerService.issueRefreshToken(String.valueOf(user.getId()), null);

        // Rotate: allow JTI mới, blacklist JTI cũ đến khi hết hạn
        try {
            Map<String, Object> newClaims = jwtIssuerService.getClaimsFromToken(newRefreshToken);
            String newJti = (String) newClaims.get("jti");
            Number newExpNum = (Number) newClaims.get("exp");
            if (newJti != null && newExpNum != null) {
                long newTtl = Math.max(1, newExpNum.longValue() - now);
                refreshTokenService.allow(sub, newJti, newTtl);
            }
            // Blacklist JTI cũ để tránh replay đến khi hết hạn
            refreshTokenService.blacklist(oldJti, ttlRemain);
        } catch (RuntimeException e) {
            log.warn("Refresh rotation housekeeping failed: {}", e.getMessage());
        }

        log.info("Token refreshed successfully for user: {}", user.getUsername());
        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    /**
     * Get user profile by ID
     * @param userId the user ID
     * @return User entity
     */
    public User getUserProfile(Long userId) {
        log.info("Getting user profile for ID: {}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Update user profile
     * @param userId the user ID
     * @param email the new email
     * @param fullName the new full name
     * @return updated User entity
     */
    public User updateUserProfile(Long userId, String email, String fullName) {
        log.info("Updating user profile for ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(email);
        }

        if (fullName != null) {
            user.setFullName(fullName);
        }

        User updatedUser = userRepository.save(user);
        log.info("User profile updated successfully: {}", updatedUser.getUsername());

        return updatedUser;
    }

    /**
     * Load user details by username for Spring Security
     * @param username the username
     * @return UserDetails
     * @throws UsernameNotFoundException if user not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(new ArrayList<>()) // Có thể thêm roles từ database sau
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    // Helpers
    private String resolveDeviceId(HttpServletRequest request) {
        if (request == null) return null;
        String[] keys = new String[]{"X-Device-Id", "X-Device-ID", "X-Device"};
        for (String k : keys) {
            String v = request.getHeader(k);
            if (StringUtils.hasText(v)) return sanitizeDeviceId(v);
        }
        // Fallback: derive from UA + client IP
        String ua = defaultString(request.getHeader("User-Agent"));
        String ip = getClientIP(request);
        String seed = ua + "|" + ip;
        return shortHash(seed);
    }

    private String getClientIP(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xri)) return xri;
        return request.getRemoteAddr();
    }

    private String sanitizeDeviceId(String raw) {
        // Keep it URL-safe and short; strip spaces; limit length
        String s = raw.trim();
        if (s.length() > 128) s = s.substring(0, 128);
        return s;
    }

    private String defaultString(String s) { return s == null ? "" : s; }

    private String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            // return first 16 chars to keep concise
            String hex = sb.toString();
            return hex.substring(0, Math.min(16, hex.length()));
        } catch (Exception e) {
            return null;
        }
    }
}
