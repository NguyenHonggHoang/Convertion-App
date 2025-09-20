package com.example.converter.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class RefreshTokenService {

  private static final String ALLOW_PREFIX = "rt:allow:"; // per-subject -> current allowed JTI
  private static final String BLACK_PREFIX = "rt:black:"; // per-JTI blacklist flag

  private final StringRedisTemplate redis;

  public RefreshTokenService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  private String keyAllow(String sub) {
    return ALLOW_PREFIX + sub;
  }

  private String keyBlack(String jti) {
    return BLACK_PREFIX + jti;
  }

  public void allow(String sub, String jti, long ttlSec) {
    if (sub == null || sub.isBlank() || jti == null || jti.isBlank()) return;
    long ttl = Math.max(1, ttlSec);
    try {
      redis.opsForValue().set(keyAllow(sub), jti, Duration.ofSeconds(ttl));
    } catch (Exception e) {
      log.warn("RefreshTokenService.allow failed for sub={}, jti={} : {}", sub, jti, e.getMessage());
    }
  }

  public boolean isAllowed(String sub, String jti) {
    if (sub == null || sub.isBlank() || jti == null || jti.isBlank()) return false;
    try {
      String allowed = redis.opsForValue().get(keyAllow(sub));
      return jti.equals(allowed);
    } catch (Exception e) {
      // Conservative default: if Redis fails, deny refresh
      log.warn("RefreshTokenService.isAllowed failed for sub={}, jti={} : {}", sub, jti, e.getMessage());
      return false;
    }
  }

  public void blacklist(String jti, long ttlSec) {
    if (jti == null || jti.isBlank()) return;
    long ttl = Math.max(1, ttlSec);
    try {
      redis.opsForValue().set(keyBlack(jti), "1", Duration.ofSeconds(ttl));
    } catch (Exception e) {
      log.warn("RefreshTokenService.blacklist failed for jti={} : {}", jti, e.getMessage());
    }
  }

  public void revokeAll(String sub) {
    if (sub == null || sub.isBlank()) return;
    try {
      redis.delete(keyAllow(sub));
    } catch (Exception e) {
      log.warn("RefreshTokenService.revokeAll failed for sub={} : {}", sub, e.getMessage());
    }
  }

  public boolean isBlacklisted(String jti) {
    if (jti == null || jti.isBlank()) return false;
    try {
      return Boolean.TRUE.equals(redis.hasKey(keyBlack(jti)));
    } catch (Exception e) {
      // Conservative default: if Redis fails, treat as blacklisted to block refresh replay
      log.warn("RefreshTokenService.isBlacklisted failed for jti={} : {}", jti, e.getMessage());
      return true;
    }
  }
}
