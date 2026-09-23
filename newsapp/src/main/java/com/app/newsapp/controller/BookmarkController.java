package com.app.newsapp.controller;

import com.app.newsapp.model.Bookmark;
import com.app.newsapp.service.BookmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    @Autowired
    private BookmarkService bookmarkService;

    @PostMapping("/toggle/{articleId}")
    public ResponseEntity<?> toggleBookmark(Authentication authentication, @PathVariable Long articleId) {
        String statusMessage = bookmarkService.toggleBookmark(authentication.getName(), articleId);
        return ResponseEntity.ok(Map.of("message", statusMessage));
    }

    @GetMapping
    public ResponseEntity<Page<Bookmark>> getUserBookmarks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Bookmark> bookmarks = bookmarkService.getUserBookmarks(authentication.getName(), page, size);
        return ResponseEntity.ok(bookmarks);
    }
}