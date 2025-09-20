package com.example.converter.controller.news;

import com.example.converter.dto.news.NewsArticleDTO;
import com.example.converter.dto.user.UserAlertRequest;
import com.example.converter.dto.user.UserAlertResponse;
import com.example.converter.service.news.NewsService;
import com.example.converter.service.user.UserAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "News and Alerts", description = "APIs for news and alert configurations")
public class NewsController {

    private final NewsService newsService;
    private final UserAlertService userAlertService;

    @GetMapping("/news")
    @Operation(summary = "Get news articles with sentiment analysis")
    public ResponseEntity<List<NewsArticleDTO>> getNews(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(newsService.getAllNews(page, size));
    }

    @PostMapping("/alerts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create new alert configuration")
    public ResponseEntity<UserAlertResponse> createAlert(@Valid @RequestBody UserAlertRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(userAlertService.createAlert(userId, request));
    }

    @PutMapping("/alerts/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update alert configuration")
    public ResponseEntity<UserAlertResponse> updateAlert(@PathVariable Long id,
                                                         @Valid @RequestBody UserAlertRequest request) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(userAlertService.updateAlert(id, userId, request));
    }

    @DeleteMapping("/alerts/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete alert configuration")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        userAlertService.deleteAlert(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return 1L;
    }

    @PostMapping("/news/refresh")
    @Operation(summary = "Trigger news fetch and analysis now")
    public ResponseEntity<Void> refreshNewsNow() {
        newsService.fetchAndAnalyzeNews();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/news/refresh/pair")
    @Operation(summary = "Trigger news fetch for specific currency pair")
    public ResponseEntity<Void> refreshNewsForPair(@RequestParam String base, @RequestParam String quote) {
        newsService.fetchAndAnalyzeNewsForPair(base, quote);
        return ResponseEntity.accepted().build();
    }
}
