package com.app.newsapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Service
public class OAuthCodeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Duration CODE_TTL = Duration.ofSeconds(30);

    public String issueCode(String accessToken, String refreshToken, String email) {
        String code = UUID.randomUUID().toString();
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("accessToken", accessToken);
            payload.put("refreshToken", refreshToken);
            payload.put("email", email);

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set("oauth_code:" + code, json, CODE_TTL);
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue OAuth handoff code", e);
        }
        return code;
    }


    public Optional<Map<String, String>> redeemCode(String code) {
        String redisKey = "oauth_code:" + code;
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) return Optional.empty();

        redisTemplate.delete(redisKey);

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> payload = objectMapper.readValue(json, Map.class);
            return Optional.of(payload);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}