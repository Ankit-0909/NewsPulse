package com.app.newsapp.service;

import com.app.newsapp.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {


    @Autowired
    private StringRedisTemplate redisTemplate;


    public void enforce(String bucket, String identifier, int maxAttempts, Duration window) {
        String redisKey = "rate_limit:" + bucket + ":" + identifier.toLowerCase();

        Long count = redisTemplate.opsForValue().increment(redisKey);


        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }

        if (count != null && count > maxAttempts) {
            Long ttlSeconds = redisTemplate.getExpire(redisKey);
            long minutesLeft = (ttlSeconds != null && ttlSeconds > 0)
                    ? Math.max(1, (ttlSeconds + 59) / 60)
                    : window.toMinutes();
            throw new RateLimitExceededException(
                    "Too many attempts. Please try again in " + minutesLeft + " minute(s)."
            );
        }
    }

    public void assertNotBlocked(String bucket, String identifier, int maxAttempts) {
        String redisKey = "rate_limit:" + bucket + ":" + identifier.toLowerCase();
        String countStr = redisTemplate.opsForValue().get(redisKey);

        if (countStr != null && Long.parseLong(countStr) >= maxAttempts) {
            Long ttlSeconds = redisTemplate.getExpire(redisKey);
            long minutesLeft = (ttlSeconds != null && ttlSeconds > 0)
                    ? Math.max(1, (ttlSeconds + 59) / 60)
                    : 1;
            throw new RateLimitExceededException(
                    "Too many failed attempts. Please try again in " + minutesLeft + " minute(s)."
            );
        }
    }


    public void recordFailure(String bucket, String identifier, Duration window) {
        String redisKey = "rate_limit:" + bucket + ":" + identifier.toLowerCase();
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
    }


    public void clearAttempts(String bucket, String identifier) {
        String redisKey = "rate_limit:" + bucket + ":" + identifier.toLowerCase();
        redisTemplate.delete(redisKey);
    }
}