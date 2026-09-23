package com.app.newsapp.controller;

import com.app.newsapp.model.NewsSource;
import com.app.newsapp.repository.FeedHealthRepository;
import com.app.newsapp.repository.NewsSourceRepository;
import com.app.newsapp.service.NewsIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sources")
public class AdminSourceController {

    @Autowired
    private NewsSourceRepository newsSourceRepository;

    @Autowired
    private FeedHealthRepository feedHealthRepository;


    @PostMapping
    public ResponseEntity<?> addNewsSource(@RequestBody NewsSource newsSource) {
        try {
            NewsSource savedSource = newsSourceRepository.save(newsSource);
            return ResponseEntity.ok(savedSource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Error adding source: " + e.getMessage());
        }
    }


    @GetMapping
    public ResponseEntity<List<NewsSource>> getAllSources() {
        List<NewsSource> sources = newsSourceRepository.findAll();
        return ResponseEntity.ok(sources);
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateNewsSource(@PathVariable Long id, @RequestBody NewsSource updatedSource) {
        return newsSourceRepository.findById(id)
                .map(source -> {
                    source.setSourceName(updatedSource.getSourceName());
                    source.setRssUrl(updatedSource.getRssUrl());
                    source.setCategory(updatedSource.getCategory());
                    source.setActive(updatedSource.isActive());
                    NewsSource saved = newsSourceRepository.save(source);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNewsSource(@PathVariable Long id) {
        return newsSourceRepository.findById(id)
                .map(source -> {

                    feedHealthRepository.findBySourceName(source.getSourceName())
                            .ifPresent(feedHealthRepository::delete);

                    newsSourceRepository.delete(source);
                    return ResponseEntity.ok("🗑️ Source deleted successfully with ID: " + id);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    @Autowired
    private NewsIngestionService newsIngestionService;


}