package com.example.converter.dto.news;

import com.example.converter.entity.NewsArticle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticleDTO {
    private Long id;
    private String title;
    private String summary;
    private String url;
    private String category;
    private String sentimentLabel;
    private Double sentimentScore;
    private LocalDateTime publishedAt;

    public static NewsArticleDTO fromEntity(NewsArticle article) {
        return new NewsArticleDTO(
                article.getId(),
                article.getTitle(),
                article.getSummary(),
                article.getUrl(),
                article.getCategory(),
                article.getSentimentLabel(),
                article.getSentimentScore(),
                article.getPublishedAt()
        );
    }
}
