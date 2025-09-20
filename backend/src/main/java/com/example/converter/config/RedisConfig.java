package com.example.converter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

  @Value("${spring.data.redis.host:redis}")
  private String host;

  @Value("${spring.data.redis.port:6379}")
  private int port;

  @Value("${spring.data.redis.password:}")
  private String password;

  @Bean
  public LettuceConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
    if (password != null && !password.isBlank()) {
      cfg.setPassword(password);
    }
    return new LettuceConnectionFactory(cfg);
  }

  @Bean
  public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory f) {
    return new StringRedisTemplate(f);
  }
}
