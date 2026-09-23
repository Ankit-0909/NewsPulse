package com.app.newsapp.repository;

import com.app.newsapp.model.RefreshToken;
import com.app.newsapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUser(User user);


    void deleteByToken(String token);

    @Modifying
    void deleteByUser(User user);
}