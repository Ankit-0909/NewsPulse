package com.app.newsapp.service;

import com.app.newsapp.dto.PageResponse;
import com.app.newsapp.model.Article;
import com.app.newsapp.repository.ArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.app.newsapp.repository.BookmarkRepository;
import com.app.newsapp.repository.ReadingHistoryRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ArticleService {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Cacheable(value = "newsCache", key = "#page + '-' + #size")
    public PageResponse<Article> getAllArticles(int page, int size) {
        System.out.println("💥 MySQL Se Data Fetch Ho Raha Hai! (Cache Miss)");
        Pageable pageable = PageRequest.of(page, size);
        Page<Article> articlePage = articleRepository.findAllByOrderByPublishedAtDesc(pageable);

        return new PageResponse<>(
                articlePage.getContent(),
                articlePage.getNumber(),
                articlePage.getSize(),
                articlePage.getTotalElements(),
                articlePage.getTotalPages(),
                articlePage.isLast()
        );
    }

    @Cacheable(value = "singleArticleCache", key = "#id")
    public Article getArticleById(Long id) {
        System.out.println("💥 MySQL Se Single Article Fetch Ho Raha Hai! ID: " + id);
        return articleRepository.findById(id).orElse(null);
    }
    public List<String> getAllCategories() {
        return articleRepository.findDistinctCategories();
    }

    @Cacheable(value = "categoryNewsCache", key = "#category + '-' + #page + '-' + #size")
    public PageResponse<Article> getArticlesByCategory(String category, int page, int size) {
        System.out.println("💥 MySQL Se [" + category + "] Category Ka Data Fetch Ho Raha Hai! (Cache Miss)");
        Pageable pageable = PageRequest.of(page, size);
        Page<Article> articlePage = articleRepository.findByCategoryIgnoreCaseOrderByPublishedAtDesc(category, pageable);

        return new PageResponse<>(
                articlePage.getContent(),
                articlePage.getNumber(),
                articlePage.getSize(),
                articlePage.getTotalElements(),
                articlePage.getTotalPages(),
                articlePage.isLast()
        );
    }


    public PageResponse<Article> searchArticles(String keyword, int page, int size) {
        System.out.println("💥 MySQL Se Keyword [" + keyword + "] Search Ho Raha Hai!");
        Pageable pageable = PageRequest.of(page, size);
        Page<Article> articlePage =
                articleRepository.findByTitleContainingIgnoreCaseOrOriginalContentContainingIgnoreCaseOrCategoryContainingIgnoreCaseOrderByPublishedAtDesc
                        (keyword, keyword, keyword, pageable);

        return new PageResponse<>(
                articlePage.getContent(),
                articlePage.getNumber(),
                articlePage.getSize(),
                articlePage.getTotalElements(),
                articlePage.getTotalPages(),
                articlePage.isLast()
        );
    }
    @Transactional
    public boolean deleteArticle(Long articleId) {

        if (!articleRepository.existsById(articleId)) {
            return false;
        }


        readingHistoryRepository.deleteByArticleId(articleId);


        bookmarkRepository.deleteByArticleId(articleId);


        articleRepository.deleteById(articleId);

        return true;
    }
}