package com.app.newsapp.controller;

import com.app.newsapp.model.ReadingHistory;
import com.app.newsapp.service.ReadingHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class ReadingHistoryController {

    @Autowired
    private ReadingHistoryService readingHistoryService;

    @PostMapping("/track")
    public ResponseEntity<?> trackProgress(
            Authentication authentication,
            @RequestParam Long articleId,
            @RequestParam Integer progress) {
        readingHistoryService.trackReadingProgress(authentication.getName(), articleId, progress);
        return ResponseEntity.ok(Map.of("message", "Reading progress updated to " + progress + "%"));
    }

    @GetMapping
    public ResponseEntity<Page<ReadingHistory>> getReadingHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReadingHistory> history = readingHistoryService.getUserReadingHistory(authentication.getName(), page, size);
        return ResponseEntity.ok(history);
    }
}