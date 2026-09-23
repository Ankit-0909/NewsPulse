package com.app.newsapp.controller;

import com.app.newsapp.model.FeedHealth;
import com.app.newsapp.repository.FeedHealthRepository;
import com.app.newsapp.service.NewsScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    @Autowired
    private FeedHealthRepository feedHealthRepository;

    @Autowired
    private NewsScheduler newsScheduler;


    @GetMapping("/feeds/health")
    public ResponseEntity<List<FeedHealth>> getAllFeedHealth() {
        List<FeedHealth> healthList = feedHealthRepository.findAll();
        return ResponseEntity.ok(healthList);
    }


    @PostMapping("/feeds/fetch-now")
    public ResponseEntity<?> triggerFetchNow() {
        newsScheduler.triggerComprehensiveNewsIngestion();
        return ResponseEntity.ok(Map.of(
                "message", "Feed fetch triggered! Articles will appear shortly — refresh in a few seconds."
        ));
    }
}
