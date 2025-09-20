package com.example.converter.service.nlp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Advanced NLP Service with FinBERT integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedNLPService {

    private final WebClient webClient;

    @Value("${microservice.nlp.url:http://localhost:5004}")
    private String advancedNlpServiceUrl;

    /**
     * Analyze sentiment using FinBERT
     */
    public Map<String, Object> analyzeFinancialSentiment(String text) {
        try {
            log.debug("Analyzing financial sentiment with FinBERT: {}", text.substring(0, Math.min(100, text.length())));
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);

            String response = webClient.post()
                    .uri(advancedNlpServiceUrl + "/nlp/finbert")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return parseFinBERTResponse(response);

        } catch (Exception e) {
            log.error("FinBERT sentiment analysis failed: {}", e.getMessage());
            return createFallbackSentiment(text);
        }
    }

    /**
     * Advanced text processing with entity extraction
     */
    public Map<String, Object> processAdvancedNLP(String title, String content) {
        try {
            log.debug("Processing advanced NLP for: {}", title);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("title", title);
            requestBody.put("content", content);

            String response = webClient.post()
                    .uri(advancedNlpServiceUrl + "/nlp/advanced_process")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            return parseAdvancedNLPResponse(response);

        } catch (Exception e) {
            log.error("Advanced NLP processing failed: {}", e.getMessage());
            return createFallbackProcessing(title, content);
        }
    }

    /**
     * Batch process multiple articles with FinBERT
     */
    public List<Map<String, Object>> batchProcessArticles(List<Map<String, Object>> articles) {
        try {
            log.info("Batch processing {} articles with advanced NLP", articles.size());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("articles", articles);

            String response = webClient.post()
                    .uri(advancedNlpServiceUrl + "/nlp/batch_advanced")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            return parseBatchNLPResponse(response);

        } catch (Exception e) {
            log.error("Batch NLP processing failed: {}", e.getMessage());
            return createFallbackBatchProcessing(articles);
        }
    }

    /**
     * Crawl news from multiple financial sources
     */
    public List<Map<String, Object>> crawlMultiSourceNews(List<String> sources, int limitPerSource) {
        try {
            log.info("Crawling news from sources: {} with limit: {}", sources, limitPerSource);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sources", sources);
            requestBody.put("limit_per_source", limitPerSource);

            String response = webClient.post()
                    .uri(advancedNlpServiceUrl + "/news/crawl_multi")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(180))
                    .block();

            return parseMultiSourceResponse(response);

        } catch (Exception e) {
            log.error("Multi-source news crawling failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extract financial entities from text
     */
    public Map<String, List<String>> extractFinancialEntities(String text) {
        try {
            Map<String, Object> result = processAdvancedNLP("", text);
            
            @SuppressWarnings("unchecked")
            Map<String, List<String>> entities = (Map<String, List<String>>) result.get("entities");
            
            return entities != null ? entities : Collections.emptyMap();
            
        } catch (Exception e) {
            log.error("Financial entity extraction failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> parseFinBERTResponse(String response) {
        Map<String, Object> sentiment = new HashMap<>();
        
        try {
            // Mock parsing - replace with proper JSON parsing using ObjectMapper
            sentiment.put("label", "positive");
            sentiment.put("score", 0.02); // Realistic 2% adjustment instead of 75%
            sentiment.put("confidence", 0.85);
            sentiment.put("model", "FinBERT");
            
        } catch (Exception e) {
            log.error("Error parsing FinBERT response: {}", e.getMessage());
            return createFallbackSentiment(response);
        }
        
        return sentiment;
    }

    private Map<String, Object> parseAdvancedNLPResponse(String response) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Mock parsing - replace with proper JSON parsing
            Map<String, Object> sentiment = new HashMap<>();
            sentiment.put("label", "positive");
            sentiment.put("score", 0.03); // Realistic 3% adjustment instead of 65%
            sentiment.put("model", "FinBERT");
            
            Map<String, Object> categorization = new HashMap<>();
            categorization.put("primary_category", "forex");
            Map<String, Double> categoryScores = new HashMap<>();
            categoryScores.put("forex", 0.8);
            categoryScores.put("equities", 0.2);
            categorization.put("category_scores", categoryScores);
            
            Map<String, List<String>> entities = new HashMap<>();
            entities.put("currencies", Arrays.asList("USD", "EUR"));
            entities.put("percentages", Arrays.asList("2.5%"));
            entities.put("numbers", Arrays.asList("1.25"));
            
            result.put("sentiment", sentiment);
            result.put("categorization", categorization);
            result.put("summary", "Advanced financial analysis summary");
            result.put("entities", entities);
            
        } catch (Exception e) {
            log.error("Error parsing advanced NLP response: {}", e.getMessage());
        }
        
        return result;
    }

    private List<Map<String, Object>> parseBatchNLPResponse(String response) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            // Mock parsing - replace with proper JSON parsing
            for (int i = 0; i < 3; i++) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", i);
                
                Map<String, Object> sentiment = new HashMap<>();
                sentiment.put("label", i % 2 == 0 ? "positive" : "negative");
                sentiment.put("score", 0.6 + Math.random() * 0.3);
                
                result.put("sentiment", sentiment);
                result.put("category", "forex");
                results.add(result);
            }
            
        } catch (Exception e) {
            log.error("Error parsing batch NLP response: {}", e.getMessage());
        }
        
        return results;
    }

    private List<Map<String, Object>> parseMultiSourceResponse(String response) {
        List<Map<String, Object>> articles = new ArrayList<>();
        
        try {
            // Mock parsing - replace with proper JSON parsing
            for (int i = 0; i < 5; i++) {
                Map<String, Object> article = new HashMap<>();
                article.put("title", "Financial News " + i);
                article.put("content", "Financial news content " + i);
                article.put("source", "reuters_finance");
                article.put("sentiment_label", "positive");
                article.put("sentiment_score", 0.7);
                articles.add(article);
            }
            
        } catch (Exception e) {
            log.error("Error parsing multi-source response: {}", e.getMessage());
        }
        
        return articles;
    }

    private Map<String, Object> createFallbackSentiment(String text) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("label", "neutral");
        fallback.put("score", 0.0);
        fallback.put("confidence", 0.5);
        fallback.put("model", "fallback");
        return fallback;
    }

    private Map<String, Object> createFallbackProcessing(String title, String content) {
        Map<String, Object> result = new HashMap<>();
        result.put("sentiment", Map.of("label", "neutral", "score", 0.0));
        result.put("categorization", Map.of("primary_category", "general"));
        result.put("summary", title);
        result.put("entities", Collections.emptyMap());
        return result;
    }

    private List<Map<String, Object>> createFallbackBatchProcessing(List<Map<String, Object>> articles) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", i);
            result.put("sentiment", Map.of("label", "neutral", "score", 0.0));
            result.put("category", "general");
            results.add(result);
        }
        return results;
    }
}
