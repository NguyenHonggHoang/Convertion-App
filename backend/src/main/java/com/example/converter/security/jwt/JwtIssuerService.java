package com.example.converter.security.jwt;

import com.example.converter.config.JwtProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtIssuerService {
    private final JwtProperties jwtProperties;
    private final PemUtil pemUtil;

    public JwtIssuerService(JwtProperties jwtProperties, PemUtil pemUtil) {
        this.jwtProperties = jwtProperties;
        this.pemUtil = pemUtil;
    }

    /**
     * Phát hành token tùy chỉnh với các tham số linh hoạt
     * @param subject subject của token
     * @param roles danh sách roles
     * @param deviceId device ID
     * @param ttlSeconds thời gian sống của token
     * @param extraClaims các claim bổ sung
     * @return JWT token string
     */
    public String issueToken(String subject, List<String> roles, String deviceId, long ttlSeconds, Map<String, Object> extraClaims) {
        try {
            String activeKid = jwtProperties.getActiveKid();
            RSAPrivateKey privateKey = pemUtil.getPrivateKey(activeKid);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(activeKid)
                    .build();

            Instant now = Instant.now();
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(subject) // sub
                    .issuer(jwtProperties.getIssuer())
                    .audience(jwtProperties.getAudience())
                    .jwtID(UUID.randomUUID().toString())
                    .notBeforeTime(Date.from(now))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)));

            if (roles != null && !roles.isEmpty()) {
                claimsBuilder.claim("roles", roles);
            }

            if (deviceId != null) {
                claimsBuilder.claim("device_id", deviceId);
            }

            if (extraClaims != null) {
                for (Map.Entry<String, Object> entry : extraClaims.entrySet()) {
                    claimsBuilder.claim(entry.getKey(), entry.getValue());
                }
            }

            JWTClaimsSet claims = claimsBuilder.build();
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(privateKey));

            return signedJWT.serialize();

        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo token: " + e.getMessage(), e);
        }
    }

    /**
     * Phát hành access token cho user ID
     */
    public String issueAccessToken(String userId, List<String> roles, String deviceId) {
        return issueToken(userId, roles, deviceId, jwtProperties.getAccessTtlSec(),
            Map.of("token_type", "access"));
    }

    /**
     * Phát hành refresh token
     * @param userId ID của user
     * @param deviceId ID thiết bị
     * @return JWT refresh token string
     */
    public String issueRefreshToken(String userId, String deviceId) {
        long refreshTtl = 2592000; // 30 ngày
        return issueToken(userId, null, deviceId, refreshTtl,
            Map.of("token_type", "refresh"));
    }

    /**
     * Validate JWT token
     * @param token JWT token string
     * @return true nếu token hợp lệ
     */
    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            String kid = signedJWT.getHeader().getKeyID();
            if (kid == null) {
                log.error("Token không có kid trong header");
                return false;
            }

            RSAPrivateKey privateKey = pemUtil.getPrivateKey(kid);
            RSAPublicKey publicKey = getPublicKeyFromPrivate(privateKey);

            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                log.error("Token signature không hợp lệ");
                return false;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                log.error("Token đã hết hạn");
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Lỗi khi validate token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Lấy claims từ JWT token
     * @param token JWT token string
     * @return Map chứa các claims
     */
    public Map<String, Object> getClaimsFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            return claims.toJSONObject();
        } catch (Exception e) {
            throw new RuntimeException("Không thể parse claims từ token: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo public key từ private key (dùng để verify)
     */
    private RSAPublicKey getPublicKeyFromPrivate(RSAPrivateKey privateKey) throws Exception {
        if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey) {
            java.security.interfaces.RSAPrivateCrtKey privKeySpec =
                (java.security.interfaces.RSAPrivateCrtKey) privateKey;

            java.security.spec.RSAPublicKeySpec publicKeySpec =
                new java.security.spec.RSAPublicKeySpec(
                    privKeySpec.getModulus(),
                    privKeySpec.getPublicExponent()
                );

            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
        }
        throw new RuntimeException("Private key không phải là RSAPrivateCrtKey");
    }
}
