package com.app.newsapp.service;

import com.app.newsapp.model.Article;
import com.app.newsapp.model.Bookmark;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.ArticleRepository;
import com.app.newsapp.repository.BookmarkRepository;
import com.app.newsapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class BookmarkService {

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;

    public String toggleBookmark(String email, Long articleId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article not found"));

        Optional<Bookmark> existingBookmark = bookmarkRepository.findByUserAndArticle(user, article);

        if (existingBookmark.isPresent()) {
            bookmarkRepository.deleteByUserAndArticle(user, article);
            return "Article removed from bookmarks.";
        } else {
            Bookmark bookmark = new Bookmark();
            bookmark.setUser(user);
            bookmark.setArticle(article);
            bookmark.setSavedAt(LocalDateTime.now());
            bookmarkRepository.save(bookmark);
            return "Article added to bookmarks successfully.";
        }
    }

    public Page<Bookmark> getUserBookmarks(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Pageable pageable = PageRequest.of(page, size);
        return bookmarkRepository.findByUserOrderBySavedAtDesc(user, pageable);
    }
}