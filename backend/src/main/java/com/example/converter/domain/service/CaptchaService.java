package com.example.converter.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class CaptchaService {

  private final HttpClient http;
  private final ObjectMapper mapper;

  @Value("${captcha.provider:recaptcha}")
  private String provider;

  @Value("${captcha.secret:}")
  private String secret;

  @Value("${app.captcha.dev-bypass:false}")
  private boolean devBypass;

  @Value("${app.captcha.min-score:0.5}")
  private double minScore;

  public CaptchaService(ObjectMapper mapper) {
    this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    this.mapper = mapper;
  }

  public boolean verify(String token, String remoteIp) {
    log.info("CaptchaService.verify called - token: {}, remoteIp: {}, devBypass: {}",
             token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null",
             remoteIp, devBypass);

    String trimmedSecret = secret != null ? secret.trim() : "";
    String secretFingerprint = trimmedSecret.isEmpty() ? "empty" : 
        Integer.toHexString(trimmedSecret.hashCode()).substring(0, 8);
    log.info(" Secret fingerprint: {} (length: {}, ends with: {})",
             secretFingerprint, 
             trimmedSecret.length(),
             trimmedSecret.length() > 3 ? trimmedSecret.substring(trimmedSecret.length() - 3) : "N/A");

    if (token == null || token.isBlank()) {
      log.warn("No captcha token provided, returning devBypass: {}", devBypass);
      return devBypass;
    }

    log.info("Captcha config - provider: {}, secret: {}", provider,
             secret.isBlank() ? "empty" : secret.substring(0, Math.min(10, secret.length())) + "...");

    if (secret.isBlank()) {
      log.warn("No captcha secret configured, returning devBypass: {}", devBypass);
      return devBypass;
    }

    try {
      String url = provider.equalsIgnoreCase("hcaptcha")
              ? "https://hcaptcha.com/siteverify"
              : "https://www.google.com/recaptcha/api/siteverify";

      log.info("Making captcha verification request to: {}", url);

      StringBuilder form = new StringBuilder();
      form.append("secret=").append(URLEncoder.encode(secret, StandardCharsets.UTF_8));
      form.append("&response=").append(URLEncoder.encode(token, StandardCharsets.UTF_8));

      boolean skipRemoteIp = remoteIp == null || remoteIp.isBlank() || 
                           remoteIp.equals("0:0:0:0:0:0:0:1") || remoteIp.equals("::1");
      
      if (!skipRemoteIp) {
        form.append("&remoteip=").append(URLEncoder.encode(remoteIp, StandardCharsets.UTF_8));
        log.info("Including remoteip: {}", remoteIp);
      } else {
        log.info("Skipping remoteip for localhost: {}", remoteIp);
      }

      log.info("Final request body: {}", form.toString().replaceAll("secret=[^&]*", "secret=***"));

      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(8))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("User-Agent", "converter-app/1.0")
              .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
              .build();

      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      log.info("Captcha API response status: {}", res.statusCode());

      if (res.statusCode() != 200) {
        log.error("Captcha API returned non-200 status: {}, returning devBypass: {}", res.statusCode(), devBypass);
        return devBypass;
      }

      String body = res.body();
      log.info("Captcha API response body: {}", body);

      Map<String, Object> json = mapper.readValue(body, Map.class);

      Object successObj = json.get("success");
      boolean success = Boolean.TRUE.equals(successObj);
      log.info("Captcha success field: {}", success);

      if (!success) {
        Object errorCodes = json.get("error-codes");
        log.warn("Captcha verification failed, error-codes: {}", errorCodes);
        return false;
      }

      Object scoreObj = json.get("score");
      if (scoreObj instanceof Number) {
        double score = ((Number) scoreObj).doubleValue();
        log.info("Captcha score: {}, minScore: {}", score, minScore);
        if (score < minScore) {
          log.warn("Captcha score {} below minimum {}", score, minScore);
          return false;
        }
      }

      log.info("Captcha verification successful");
      return true;
    } catch (Exception e) {
      log.error("Captcha verification failed with exception, returning devBypass: {}", devBypass, e);
      return devBypass;
    }
  }
}
