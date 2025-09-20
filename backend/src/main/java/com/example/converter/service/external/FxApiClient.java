package com.example.converter.service.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FxApiClient {
    private final WebClient webClient;

    @Value("${external.currency.api.url}")
    private String externalApiUrl;

    @Value("${external.currency.api.key}")
    private String externalApiKey;

    /**
     * Fetches the latest base foreign exchange rates from an external API.
     * The rates are cached for performance and to reduce API calls.
     *
     * @return A map of currency codes to their exchange rates.
     */
    public Map<String, Double> fetchBaseRates(String baseCurrency) {
        String base = baseCurrency.trim().toUpperCase();
        log.info("Fetching base exchange rates for currency: {}", base);
        try {
            String baseUrl = externalApiUrl.endsWith("/") ? externalApiUrl : externalApiUrl + "/";
            String url = baseUrl + externalApiKey + "/latest/" + base;

            var node = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                    .timeout(Duration.ofSeconds(3))
                    .retryWhen(reactor.util.retry.Retry.fixedDelay(3, Duration.ofSeconds(1)))
                    .block();

            if (node == null) {
                log.error("Failed to fetch rates from external API. Response is null or missing 'rates' field.");
                return Map.of();
            }
            if( node.has("result") && !"success".equals(node.get("result").asText())) {
                log.error("External API returned an error: {}", node.get("error-type").asText());
                return Map.of();
            }
            var ratesNode = node.has("conversion_rates") ? node.get("conversion_rates") : node.get("rates");
            if (ratesNode == null || !ratesNode.isObject()) {
                log.error("Invalid response format from external API. 'rates' field is missing or not an object.");
                return Map.of();
            }
            Map<String, Double> rates = new java.util.HashMap<>();
            ratesNode.fields().forEachRemaining(e -> {
                if(e.getValue().isNumber()) rates.put(e.getKey().toUpperCase(), e.getValue().asDouble());
            });
            rates.putIfAbsent(base, 1.0);
            log.info("Successfully fetched base exchange rates for currency: {}", base);
            return rates;

        } catch (Exception e){
            log.warn("FX API call failed for base currency {}: {}", baseCurrency, e.getMessage());
            return Map.of();
        }

    }
}
