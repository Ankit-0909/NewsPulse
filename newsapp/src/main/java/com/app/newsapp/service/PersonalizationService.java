package com.app.newsapp.service;

import com.app.newsapp.model.Article;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.ArticleRepository;
import com.app.newsapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@Transactional
public class PersonalizationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;


    public void subscribeToCategory(String email, String category) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getSubscribedCategories().add(category.trim());
        userRepository.save(user);
    }

    public void unsubscribeFromCategory(String email, String category) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getSubscribedCategories().remove(category.trim());
        userRepository.save(user);
    }


    public void subscribeToSource(String email, String source) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getSubscribedSources().add(source.trim());
        userRepository.save(user);
    }

    public void unsubscribeFromSource(String email, String source) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getSubscribedSources().remove(source.trim());
        userRepository.save(user);
    }


    public Page<Article> getForYouFeed(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<String> categories = user.getSubscribedCategories();
        Set<String> sources = user.getSubscribedSources();
        Pageable pageable = PageRequest.of(page, size);

        if (categories.isEmpty() && sources.isEmpty()) {
            return articleRepository.findAllByOrderByPublishedAtDesc(pageable);
        }

        Set<String> safeCategories = categories.isEmpty() ? Set.of("##NONE##") : categories;
        Set<String> safeSources = sources.isEmpty() ? Set.of("##NONE##") : sources;

        return articleRepository.findByCategoryInOrSourceNameInOrderByPublishedAtDesc(
                safeCategories, safeSources, pageable);
    }
}