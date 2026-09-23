package com.app.newsapp.controller;

import com.app.newsapp.dto.PageResponse;
import com.app.newsapp.model.Article;
import com.app.newsapp.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "*")
public class ArticleController {

    @Autowired
    private ArticleService articleService;


    @GetMapping
    public ResponseEntity<PageResponse<Article>> getAllArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<Article> articles = articleService.getAllArticles(page, size);
        return ResponseEntity.ok(articles);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Article> getArticleById(@PathVariable Long id) {
        Article article = articleService.getArticleById(id);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(article);
    }


    @GetMapping("/category/{category}")
    public ResponseEntity<PageResponse<Article>> getArticlesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<Article> articles = articleService.getArticlesByCategory(category, page, size);
        return ResponseEntity.ok(articles);
    }


    @GetMapping("/search")
    public ResponseEntity<PageResponse<Article>> searchArticles(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        PageResponse<Article> articles = articleService.searchArticles(keyword, page, size);
        return ResponseEntity.ok(articles);
    }
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories() {
        return ResponseEntity.ok(articleService.getAllCategories());
    }
}