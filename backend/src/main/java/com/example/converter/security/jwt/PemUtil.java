package com.example.converter.security.jwt;

import com.example.converter.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class để parse private key RSA PKCS#8 từ biến môi trường
 * Format: kid1::BASE64_PKCS8|kid2::BASE64_PKCS8
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PemUtil {

    private final JwtProperties jwtProperties;
    private Map<String, RSAPrivateKey> keyCache = new HashMap<>();

    /**
     * Lấy private key theo kid
     */
    public RSAPrivateKey getPrivateKey(String kid) {
        if (keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        String keys = jwtProperties.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new RuntimeException("JWT_PRIVATE_KEYS not configured");
        }

        // Parse format: kid1::BASE64_PKCS8|kid2::BASE64_PKCS8
        String[] keyPairs = keys.split("\\|");
        for (String keyPair : keyPairs) {
            String[] parts = keyPair.split("::", 2);
            if (parts.length == 2 && parts[0].equals(kid)) {
                try {
                    RSAPrivateKey privateKey = parsePrivateKey(parts[1]);
                    keyCache.put(kid, privateKey);
                    log.info("Loaded private key for kid: {}", kid);
                    return privateKey;
                } catch (Exception e) {
                    log.error("Failed to parse private key for kid: {}", kid, e);
                    throw new RuntimeException("Failed to parse private key for kid: " + kid, e);
                }
            }
        }

        throw new RuntimeException("Private key not found for kid: " + kid);
    }

    /**
     * Parse tất cả private keys từ env variable - dùng cho JwksController
     * @param keys chuỗi format: kid1::BASE64_PKCS8|kid2::BASE64_PKCS8
     * @return Map<kid, PrivateKey>
     */
    public Map<String, PrivateKey> parsePrivateKeys(String keys) {
        Map<String, PrivateKey> result = new HashMap<>();

        if (keys == null || keys.isEmpty()) {
            log.warn("No JWT_PRIVATE_KEYS configured for JWKS");
            return result;
        }

        String[] keyPairs = keys.split("\\|");
        for (String keyPair : keyPairs) {
            String[] parts = keyPair.split("::", 2);
            if (parts.length == 2) {
                String kid = parts[0];
                String base64Key = parts[1];
                try {
                    RSAPrivateKey privateKey = parsePrivateKey(base64Key);
                    result.put(kid, privateKey);
                    log.info("Parsed private key for kid: {}", kid);
                } catch (Exception e) {
                    log.error("Failed to parse private key for kid: {}, skipping", kid, e);
                }
            }
        }

        return result;
    }

    /**
     * Parse private key từ BASE64 PKCS#8 string
     */
    private RSAPrivateKey parsePrivateKey(String base64Key) throws Exception {
        // Decode BASE64
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        // Create PKCS8EncodedKeySpec
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        // Generate RSA private key
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

}
