package com.app.newsapp.repository;

import com.app.newsapp.model.Article;
import com.app.newsapp.model.ReadingHistory;
import com.app.newsapp.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, Long> {

    Optional<ReadingHistory> findByUserAndArticle(User user, Article article);

    Page<ReadingHistory> findByUserOrderByLastReadAtDesc(User user, Pageable pageable);

    void deleteByArticleId(Long articleId);
}