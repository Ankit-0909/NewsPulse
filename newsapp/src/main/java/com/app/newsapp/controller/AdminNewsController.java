package com.app.newsapp.controller;

import com.app.newsapp.model.Article;
import com.app.newsapp.repository.ArticleRepository;
import com.app.newsapp.service.AiSummarizationService; // 🔥 1. Service ko import karein
import com.app.newsapp.service.ArticleCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.app.newsapp.service.ArticleService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/news")
public class AdminNewsController {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleCacheService articleCacheService;

    @Autowired
    private AiSummarizationService aiSummarizationService;

    @PostMapping
    public ResponseEntity<?> createManualNews(@RequestBody Article article) {
        try {
            article.setSourceName("Admin Desktop");
            article.setPublishedAt(LocalDateTime.now());
            if (article.getContentUrl() == null || article.getContentUrl().isEmpty()) {
                article.setContentUrl("internal-cms-" + java.util.UUID.randomUUID().toString());
            }


            Article savedArticle = articleRepository.save(article);


            try {
                aiSummarizationService.generateAndSaveSummary(savedArticle);
            } catch (Exception e) {
                System.err.println("⚠️ AI Summary generation failed for manual news, but article was saved: " + e.getMessage());
            }
            articleCacheService.clearFeedCaches();


            Article finalArticle = articleRepository.findById(savedArticle.getId()).orElse(savedArticle);

            return ResponseEntity.ok(finalArticle);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Error creating manual news: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNews(@PathVariable Long id, @RequestBody Article updatedArticle) {
        return articleRepository.findById(id)
                .map(article -> {
                    article.setTitle(updatedArticle.getTitle());
                    article.setOriginalContent(updatedArticle.getOriginalContent());
                    article.setCategory(updatedArticle.getCategory());

                    Article saved = articleRepository.save(article);
                    articleCacheService.clearFeedCaches();
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    @CacheEvict(
            value = {"newsCache", "categoryNewsCache"},
            allEntries = true
    )
    public ResponseEntity<?> deleteNews(@PathVariable Long id) {

        boolean deleted = articleService.deleteArticle(id);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                "🗑️ Article deleted successfully with ID: " + id
        );
    }
}