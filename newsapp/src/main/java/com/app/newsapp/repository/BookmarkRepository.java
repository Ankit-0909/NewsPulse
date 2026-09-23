package com.app.newsapp.repository;

import com.app.newsapp.model.Article;
import com.app.newsapp.model.Bookmark;
import com.app.newsapp.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByUserAndArticle(User user, Article article);

    Page<Bookmark> findByUserOrderBySavedAtDesc(User user, Pageable pageable);

    void deleteByUserAndArticle(User user, Article article);
    void deleteByArticleId(Long articleId);
}