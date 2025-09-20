package com.example.converter.controller.nlp;

import com.example.converter.service.nlp.AdvancedNLPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for advanced NLP APIs with FinBERT and multi-source news processing
 */
@RestController
@RequestMapping("/api/nlp")
@RequiredArgsConstructor
@Slf4j
public class AdvancedNLPController {

    private final AdvancedNLPService advancedNLPService;

    @PostMapping("/finbert")
    public ResponseEntity<Map<String, Object>> analyzeFinancialSentiment(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Text is required"));
            }
            
            log.debug("FinBERT sentiment analysis request");
            
            var sentiment = advancedNLPService.analyzeFinancialSentiment(text);
            
            return ResponseEntity.ok(sentiment);
            
        } catch (Exception e) {
            log.error("FinBERT sentiment analysis failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/advanced")
    public ResponseEntity<Map<String, Object>> processAdvancedNLP(@RequestBody Map<String, String> request) {
        try {
            String title = request.getOrDefault("title", "");
            String content = request.getOrDefault("content", "");
            
            if (title.trim().isEmpty() && content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Title or content is required"));
            }
            
            log.debug("Advanced NLP processing request");
            
            var result = advancedNLPService.processAdvancedNLP(title, content);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Advanced NLP processing failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchProcessArticles(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> articles = (List<Map<String, Object>>) request.get("articles");
            
            if (articles == null || articles.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Articles list is required"));
            }
            
            log.info("Batch NLP processing for {} articles", articles.size());
            
            var results = advancedNLPService.batchProcessArticles(articles);
            
            Map<String, Object> response = Map.of(
                "results", results,
                "count", results.size(),
                "processed_at", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Batch NLP processing failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/entities")
    public ResponseEntity<Map<String, Object>> extractFinancialEntities(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Text is required"));
            }
            
            log.debug("Financial entity extraction request");
            
            var entities = advancedNLPService.extractFinancialEntities(text);
            
            Map<String, Object> response = Map.of(
                "text", text,
                "entities", entities,
                "extracted_at", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Financial entity extraction failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/news/crawl")
    public ResponseEntity<Map<String, Object>> crawlMultiSourceNews(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> sources = (List<String>) request.get("sources");
            Integer limitPerSource = (Integer) request.getOrDefault("limit_per_source", 20);
            
            if (sources == null || sources.isEmpty()) {
                // Default sources
                sources = List.of("reuters_finance", "bloomberg", "financial_times");
            }
            
            log.info("Multi-source news crawling from: {}", sources);
            
            var articles = advancedNLPService.crawlMultiSourceNews(sources, limitPerSource);
            
            Map<String, Object> response = Map.of(
                "articles", articles,
                "count", articles.size(),
                "sources", sources,
                "crawled_at", java.time.LocalDateTime.now().toString()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Multi-source news crawling failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> getNLPCapabilities() {
        try {
            Map<String, Object> capabilities = Map.of(
                "sentiment_analysis", Map.of(
                    "models", List.of("FinBERT", "Lexicon-based fallback"),
                    "description", "Financial sentiment analysis optimized for market news"
                ),
                "entity_extraction", Map.of(
                    "entities", List.of("currencies", "percentages", "numbers", "companies", "indicators"),
                    "description", "Extract financial entities and market indicators"
                ),
                "categorization", Map.of(
                    "categories", List.of("forex", "equities", "commodities", "crypto", "macroeconomic"),
                    "description", "Classify financial news by market category"
                ),
                "news_sources", Map.of(
                    "sources", List.of("reuters_finance", "bloomberg", "financial_times"),
                    "description", "Multi-source news aggregation and analysis"
                ),
                "advanced_features", List.of(
                    "FinBERT sentiment scoring",
                    "Financial entity recognition", 
                    "Multi-category classification",
                    "Intelligent summarization",
                    "Batch processing"
                )
            );
            
            return ResponseEntity.ok(capabilities);
            
        } catch (Exception e) {
            log.error("Failed to get NLP capabilities", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
