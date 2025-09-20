package com.example.converter.repository;

import com.example.converter.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for NewsArticle Entity.
 * Provides methods to save and query news articles, including search by category or sort by date.
 */
@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    /**
     * Find news articles by category
     * @param category the category to search for
     * @return List of news articles in the category
     */
    @Query("SELECT n FROM NewsArticle n WHERE n.category = ?1 ORDER BY n.publishedAt DESC")
    List<NewsArticle> findByCategoryOrderByPublishedAtDesc(String category);

    /**
     * Find news articles by sentiment label
     * @param sentimentLabel the sentiment label to search for
     * @return List of news articles with the sentiment label
     */
    List<NewsArticle> findBySentimentLabelOrderByPublishedAtDesc(String sentimentLabel);

    /**
     * Find latest 10 news articles
     * @return List of latest 10 news articles
     */
    List<NewsArticle> findTop10ByOrderByPublishedAtDesc();

    /**
     * Find news articles by category and sentiment
     * @param category the category to search for
     * @param sentimentLabel the sentiment label to search for
     * @return List of news articles matching both criteria
     */
    List<NewsArticle> findByCategoryAndSentimentLabelOrderByPublishedAtDesc(String category, String sentimentLabel);

    /**
     * Check if article exists by URL
     * @param url the URL to check
     * @return true if article exists, false otherwise
     */
    boolean existsByUrl(String url);
}
