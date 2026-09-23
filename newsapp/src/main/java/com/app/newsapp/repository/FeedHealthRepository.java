package com.app.newsapp.repository;

import com.app.newsapp.model.FeedHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FeedHealthRepository extends JpaRepository<FeedHealth, Long> {
    

    Optional<FeedHealth> findBySourceName(String sourceName);
}