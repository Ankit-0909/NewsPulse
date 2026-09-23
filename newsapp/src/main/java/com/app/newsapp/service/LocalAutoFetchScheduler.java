package com.app.newsapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(name = "app.scheduler.auto-fetch.enabled", havingValue = "true")
public class LocalAutoFetchScheduler {

    @Autowired
    private NewsScheduler newsScheduler;

    @Scheduled(fixedRate = 120000)
    public void autoFetch() {
        System.out.println("⏰ [LOCAL AUTO-FETCH] Scheduled ingestion cycle triggered.");
        newsScheduler.triggerComprehensiveNewsIngestion();
    }
}