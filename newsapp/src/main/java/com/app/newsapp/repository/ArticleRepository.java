package com.app.newsapp.repository;

import com.app.newsapp.model.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByContentUrl(String contentUrl);


    List<Article> findByAiSummaryIsNull();


    @Transactional
    @Modifying
    @Query("UPDATE Article a SET a.aiSummary = :summary WHERE a.id = :id")
    void updateAiSummary(@Param("id") Long id, @Param("summary") String summary);
    Page<Article> findAllByOrderByPublishedAtDesc(Pageable pageable);

    boolean existsByTitle(String title);

    Page<Article> findByCategoryInOrSourceNameInOrderByPublishedAtDesc(
            Collection<String> categories,
            Collection<String> sources,
            Pageable pageable
    );

    List<Article> findAllByOrderByPublishedAtDesc();

    Page<Article> findByCategoryIgnoreCaseOrderByPublishedAtDesc(String category, Pageable pageable);


    Page<Article> findByTitleContainingIgnoreCaseOrOriginalContentContainingIgnoreCaseOrCategoryContainingIgnoreCaseOrderByPublishedAtDesc
    (String titleKeyword, String contentKeyword, String categoryKeyword, Pageable pageable);
    @Query("SELECT DISTINCT a.category FROM Article a WHERE a.category IS NOT NULL ORDER BY a.category")
    List<String> findDistinctCategories();
}