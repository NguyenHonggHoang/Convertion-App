package com.example.converter.domain.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AccessTokenService {

  private static final String BLACK_PREFIX = "at:black:";
  private static final String BAN_PREFIX   = "at:ban:";

  private final StringRedisTemplate redis;

  public AccessTokenService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  private String keyBlack(String jti) {
    return BLACK_PREFIX + jti;
  }

  private String keyBan(String sub) {
    return BAN_PREFIX + sub;
  }

  public void revokeAllSinceNow(String sub) {
    if (sub == null || sub.isBlank()) return;
    long now = System.currentTimeMillis() / 1000L;
    redis.opsForValue().set(keyBan(sub), Long.toString(now));
  }

  public boolean revokedByEpoch(String sub, long iatEpoch) {
    if (sub == null || sub.isBlank()) return false;
    String s = redis.opsForValue().get(keyBan(sub));
    if (s == null) return false;
    try {
      long ban = Long.parseLong(s);
      return iatEpoch < ban;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
