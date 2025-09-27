package com.example.converter.security.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {
  private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

  private final StringRedisTemplate redis;
  public TokenBlacklistService(StringRedisTemplate redis) { this.redis = redis; }

  private String key(String jti) { return "bl:access:jti:" + jti; }

  public void blacklist(String jti, long ttlSeconds) {
    try {
      redis.opsForValue().set(key(jti), "1", ttlSeconds, TimeUnit.SECONDS);
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable while blacklisting token jti={}, proceeding without persisting blacklist: {}", jti, ex.getMessage());
    } catch (RuntimeException ex) {
      log.warn("Unexpected error writing to Redis for token jti={}, proceeding: {}", jti, ex.getMessage());
    }
  }

  public boolean isBlacklisted(String jti) {
    try {
      String v = redis.opsForValue().get(key(jti));
      return v != null;
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable while checking blacklist for jti={}, defaulting to NOT blacklisted: {}", jti, ex.getMessage());
      return false;
    } catch (RuntimeException ex) {
      log.warn("Unexpected error reading from Redis for jti={}, defaulting to NOT blacklisted: {}", jti, ex.getMessage());
      return false;
    }
  }
}
