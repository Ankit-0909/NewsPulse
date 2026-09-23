package com.app.newsapp.controller;

import com.app.newsapp.model.Article;
import com.app.newsapp.service.PersonalizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/personalization")
public class PersonalizationController {

    @Autowired
    private PersonalizationService personalizationService;


    @PostMapping("/categories/subscribe")
    public ResponseEntity<?> subscribeCategory(Authentication authentication, @RequestParam String category) {
        personalizationService.subscribeToCategory(authentication.getName(), category);
        return ResponseEntity.ok(Map.of("message", "Subscribed to category: " + category));
    }



    @PostMapping("/categories/unsubscribe")
    public ResponseEntity<?> unsubscribeCategory(Authentication authentication, @RequestParam String category) {
        personalizationService.unsubscribeFromCategory(authentication.getName(), category);
        return ResponseEntity.ok(Map.of("message", "Unsubscribed from category: " + category));
    }

    @PostMapping("/sources/subscribe")
    public ResponseEntity<?> subscribeSource(Authentication authentication, @RequestParam String source) {
        personalizationService.subscribeToSource(authentication.getName(), source);
        return ResponseEntity.ok(Map.of("message", "Subscribed to source: " + source));
    }

    @PostMapping("/sources/unsubscribe")
    public ResponseEntity<?> unsubscribeSource(Authentication authentication, @RequestParam String source) {
        personalizationService.unsubscribeFromSource(authentication.getName(), source);
        return ResponseEntity.ok(Map.of("message", "Unsubscribed from source: " + source));
    }

    @GetMapping("/for-you")
    public ResponseEntity<Page<Article>> getForYouFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Article> feed = personalizationService.getForYouFeed(authentication.getName(), page, size);
        return ResponseEntity.ok(feed);
    }
}