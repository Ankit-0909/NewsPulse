package com.app.newsapp.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class ArticleCacheService {

    @CacheEvict(
            value = {
                    "newsCache",
                    "categoryNewsCache"
            },
            allEntries = true
    )
    public void clearFeedCaches() {

        System.out.println(
                "🧹 News feed caches cleared!"
        );
    }
}