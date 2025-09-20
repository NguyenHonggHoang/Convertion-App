package com.example.converter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String activeKid = "dev-1";
    private String keys;
    private String issuer = "auth-service";
    private String audience = "converter-backend";
    private int accessTtlSec = 900;
    private String jwksUri;
    private int jwksRefreshSeconds = 300;

    private int leewaySeconds = 30;
    private List<String> issuers = List.of("auth-service", "converter-backend");
    private List<String> audiences = List.of("converter-backend", "converter-api");
}
