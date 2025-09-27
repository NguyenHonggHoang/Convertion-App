package com.example.converter.service.news;

import com.example.converter.dto.news.NewsArticleDTO;
import com.example.converter.entity.NewsArticle;
import com.example.converter.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final NewsArticleRepository newsArticleRepository;
    private final WebClient webClient;

    @Value("${microservice.crawl.url}")
    private String crawlServiceUrl;

    @Value("${microservice.sentiment.url}")
    private String sentimentServiceUrl;

    public List<NewsArticleDTO> getAllNews(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending());

        return newsArticleRepository.findAll(pageable)
                .stream()
                .map(NewsArticleDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedDelay = 3600000)
    public void fetchAndAnalyzeNews() {
        try {
            log.info("Fetching news from crawl service...");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(crawlServiceUrl)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("news")) {
                log.warn("No news received from crawl service");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newsList = (List<Map<String, Object>>) response.get("news");

            for (Map<String, Object> item : newsList) {
                String url = String.valueOf(item.get("url"));
                if (newsArticleRepository.existsByUrl(url)) {
                    continue;
                }

                String title = String.valueOf(item.get("title"));
                String content = String.valueOf(item.get("content"));
                String category = String.valueOf(item.get("category"));
                String summary = String.valueOf(item.getOrDefault("summary", ""));
                String publishedAtStr = String.valueOf(item.get("published_at"));

                LocalDateTime publishedAt;
                try {
                    if (publishedAtStr.contains("+") || publishedAtStr.contains("Z")) {
                        String cleanDateTime = publishedAtStr.replaceAll("\\+\\d{2}:\\d{2}|Z", "");
                        publishedAt = LocalDateTime.parse(cleanDateTime);
                    } else {
                        publishedAt = LocalDateTime.parse(publishedAtStr);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse published_at '{}', using current time", publishedAtStr);
                    publishedAt = LocalDateTime.now();
                }

                String sentimentLabel = String.valueOf(item.getOrDefault("sentiment_label", "neutral"));
                Double sentimentScore = 0.0;
                Object scoreObj = item.get("sentiment_score");
                if (scoreObj != null) {
                    try { sentimentScore = Double.valueOf(scoreObj.toString()); } catch (Exception ignored) {}
                } else {
                    Map<String, String> sentimentReq = Map.of("text", title + ". " + content);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sentimentRes = webClient.post()
                            .uri(sentimentServiceUrl)
                            .bodyValue(sentimentReq)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();
                    sentimentLabel = sentimentRes != null ? String.valueOf(sentimentRes.getOrDefault("label", "neutral")) : sentimentLabel;
                    if (sentimentRes != null && sentimentRes.get("score") != null) {
                        try { sentimentScore = Double.valueOf(sentimentRes.get("score").toString()); } catch (Exception ignored) {}
                    }
                }

                NewsArticle article = new NewsArticle();
                article.setTitle(title);
                article.setContent(content);
                article.setUrl(url);
                article.setCategory(category);
                article.setSummary(summary);
                article.setPublishedAt(publishedAt);
                article.setSentimentLabel(sentimentLabel);
                article.setSentimentScore(sentimentScore);
                article.setSource("crawl-service");
                article.setCreatedAt(LocalDateTime.now());
                article.setUpdatedAt(LocalDateTime.now());

                newsArticleRepository.save(article);
            }
            log.info("News fetch and analysis completed. Saved {} articles.", newsList.size());
        } catch (Exception e) {
            log.error("Error during news fetch/analyze: {}", e.getMessage());
        }
    }

    /**
     * Fetch and analyze news for a specific currency pair (base/quote).
     * Passes base and quote to the crawl service for pair-focused crawling/filtering.
     */
    public void fetchAndAnalyzeNewsForPair(String base, String quote) {
        try {
            String uri = UriComponentsBuilder
                    .fromUriString(crawlServiceUrl)
                    .queryParam("base", base)
                    .queryParam("quote", quote)
                    .build(true)
                    .toUriString();

            log.info("Fetching pair-focused news from crawl service for {}-{}...", base, quote);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("news")) {
                log.warn("No news received from crawl service for pair {}-{}", base, quote);
                return;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> newsList = (List<Map<String, Object>>) response.get("news");
            for (Map<String, Object> item : newsList) {
                String url = String.valueOf(item.get("url"));
                if (newsArticleRepository.existsByUrl(url)) {
                    continue;
                }
                String title = String.valueOf(item.get("title"));
                String content = String.valueOf(item.get("content"));
                String category = String.valueOf(item.get("category"));
                String summary = String.valueOf(item.getOrDefault("summary", ""));
                String publishedAtStr = String.valueOf(item.get("published_at"));
                LocalDateTime publishedAt;
                try {
                    if (publishedAtStr.contains("+") || publishedAtStr.contains("Z")) {
                        String cleanDateTime = publishedAtStr.replaceAll("\\+\\d{2}:\\d{2}|Z", "");
                        publishedAt = LocalDateTime.parse(cleanDateTime);
                    } else {
                        publishedAt = LocalDateTime.parse(publishedAtStr);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse published_at '{}', using current time", publishedAtStr);
                    publishedAt = LocalDateTime.now();
                }

                String sentimentLabel = String.valueOf(item.getOrDefault("sentiment_label", "neutral"));
                Double sentimentScore = 0.0;
                Object scoreObj = item.get("sentiment_score");
                if (scoreObj != null) {
                    try { sentimentScore = Double.valueOf(scoreObj.toString()); } catch (Exception ignored) {}
                } else {
                    Map<String, String> sentimentReq = Map.of("text", title + ". " + content);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sentimentRes = webClient.post()
                            .uri(sentimentServiceUrl)
                            .bodyValue(sentimentReq)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();
                    sentimentLabel = sentimentRes != null ? String.valueOf(sentimentRes.getOrDefault("label", "neutral")) : sentimentLabel;
                    if (sentimentRes != null && sentimentRes.get("score") != null) {
                        try { sentimentScore = Double.valueOf(sentimentRes.get("score").toString()); } catch (Exception ignored) {}
                    }
                }

                NewsArticle article = new NewsArticle();
                article.setTitle(title);
                article.setContent(content);
                article.setUrl(url);
                article.setCategory(category);
                article.setSummary(summary);
                article.setPublishedAt(publishedAt);
                article.setSentimentLabel(sentimentLabel);
                article.setSentimentScore(sentimentScore);
                article.setSource("crawl-service");
                article.setCreatedAt(LocalDateTime.now());
                article.setUpdatedAt(LocalDateTime.now());

                newsArticleRepository.save(article);
            }
            log.info("Pair-focused news fetch completed for {}-{}. Saved {} articles.", base, quote, newsList.size());
        } catch (Exception e) {
            log.error("Error during pair-focused news fetch/analyze for {}-{}: {}", base, quote, e.getMessage());
        }
    }
}
