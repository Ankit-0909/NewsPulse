package com.app.newsapp.service;

import com.app.newsapp.model.Article;
import com.app.newsapp.model.ReadingHistory;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.ArticleRepository;
import com.app.newsapp.repository.ReadingHistoryRepository;
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
public class ReadingHistoryService {

    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;


    public void trackReadingProgress(String email, Long articleId, Integer progress) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article not found"));


        int finalProgress = Math.min(100, Math.max(0, progress));

        Optional<ReadingHistory> existingHistory = readingHistoryRepository.findByUserAndArticle(user, article);

        ReadingHistory history;
        if (existingHistory.isPresent()) {
            history = existingHistory.get();
            if (finalProgress > history.getProgressPercentage()) {
                history.setProgressPercentage(finalProgress);
            }
            if (history.getProgressPercentage() >= 100) {
                history.setIsCompleted(true);
            }
            history.setLastReadAt(LocalDateTime.now());
        } else {
            history = new ReadingHistory();
            history.setUser(user);
            history.setArticle(article);
            history.setProgressPercentage(finalProgress);
            history.setIsCompleted(finalProgress >= 100);
            history.setLastReadAt(LocalDateTime.now());
        }

        readingHistoryRepository.save(history);
    }


    public Page<ReadingHistory> getUserReadingHistory(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Pageable pageable = PageRequest.of(page, size);
        return readingHistoryRepository.findByUserOrderByLastReadAtDesc(user, pageable);
    }
}