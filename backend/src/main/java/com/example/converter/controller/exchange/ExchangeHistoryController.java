package com.example.converter.controller.exchange;

import com.example.converter.dto.exchange.FxHistoryResponse;
import com.example.converter.dto.exchange.FxPointDTO;
import com.example.converter.service.exchange.ExchangeHistoryService;
import com.example.converter.security.InternalApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
@Slf4j
public class ExchangeHistoryController {

    private final ExchangeHistoryService service;
    private final InternalApiKeyService internalApiKeyService;
    
    @Value("${security.apiKey.header:X-API-Key}") 
    private String apiKeyHeader;
    
    @Value("${security.internal.auth.enabled:true}")
    private boolean internalAuthEnabled;

    @GetMapping("/history")
    public ResponseEntity<?> history(
        @RequestParam @Pattern(regexp="^[A-Z]{3}$") String base,
        @RequestParam @Pattern(regexp="^[A-Z]{3}$") String quote,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required=false) @Min(1) @Max(365) Integer limit,
        HttpServletRequest req
    ) {
        // PEM-based internal API authentication
        if (internalAuthEnabled) {
            String authHeader = req.getHeader(apiKeyHeader);
            
            if (authHeader == null || authHeader.isBlank()) {
                log.warn("Missing internal API key header: {}", apiKeyHeader);
                return ResponseEntity.status(401).body(Map.of("error", "missing_api_key", "message", "Internal API key required"));
            }

            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            
            if (!internalApiKeyService.validateInternalApiKey(token)) {
                String serviceName = internalApiKeyService.getServiceNameFromToken(token);
                log.warn("Invalid internal API key from service: {}", serviceName != null ? serviceName : "unknown");
                return ResponseEntity.status(401).body(Map.of("error", "invalid_api_key", "message", "Invalid or expired internal API key"));
            }
            
            String serviceName = internalApiKeyService.getServiceNameFromToken(token);
            log.debug("Authorized internal API request from service: {}", serviceName);
        }

        FxHistoryResponse payload = service.getHistory(base, quote, from, to, limit);
        if (payload.getHistory().isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error","no data"));
        }

        // ETag = SHA-256(base|quote|count|first|last|hashRates)
        String first = payload.getHistory().get(0).getDate().toString();
        String last  = payload.getHistory().get(payload.getHistory().size()-1).getDate().toString();
        String ratesConcat = payload.getHistory().stream()
            .map(p -> p.getRate().toPlainString())
            .collect(Collectors.joining(","));
        String src = base+"|"+quote+"|"+payload.getHistory().size()+"|"+first+"|"+last+"|"+ratesConcat;
        String etag = "\""+DigestUtils.md5DigestAsHex(src.getBytes())+"\"";

        String ifNoneMatch = req.getHeader("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }

        ZonedDateTime lastModified = payload.getHistory().stream()
            .map(FxPointDTO::getDate)
            .max(LocalDate::compareTo)
            .map(d -> d.atStartOfDay(ZoneOffset.UTC))
            .orElse(ZonedDateTime.now(ZoneOffset.UTC));

        return ResponseEntity.ok()
            .eTag(etag)
            .lastModified(lastModified.toInstant().toEpochMilli())
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
            .body(payload);
    }
}