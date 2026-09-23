package com.app.newsapp.repository;

import com.app.newsapp.model.NewsSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {
    List<NewsSource> findByIsActiveTrue();
}