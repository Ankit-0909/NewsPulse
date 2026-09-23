package com.app.newsapp.service;

import com.app.newsapp.model.RefreshToken;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.RefreshTokenRepository;
import com.app.newsapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final Long refreshTokenDurationMs = 604800000L; // 7 Din (Milliseconds mein)

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;


    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash refresh token", e);
        }
    }


    @Transactional
    public String createRefreshToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        RefreshToken refreshToken = refreshTokenRepository.findByUser(user)
                .orElse(new RefreshToken());

        String rawToken = UUID.randomUUID().toString();

        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(hashToken(rawToken));

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }


    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please sign in again!");
        }
        return token;
    }


    public Optional<RefreshToken> findByToken(String rawToken) {
        return refreshTokenRepository.findByToken(hashToken(rawToken));
    }

    @Transactional
    public void revokeToken(String rawToken) {
        refreshTokenRepository.findByToken(hashToken(rawToken))
                .ifPresent(refreshToken -> refreshTokenRepository.delete(refreshToken));
    }

}