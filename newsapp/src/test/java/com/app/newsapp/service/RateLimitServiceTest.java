package com.app.newsapp.service;

import com.app.newsapp.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        rateLimitService = new RateLimitService();
        ReflectionTestUtils.setField(rateLimitService, "redisTemplate", redisTemplate);
    }



    @Test
    void enforce_firstAttempt_shouldSetExpiry() {
        String key = "rate_limit:login:test@example.com";
        when(valueOperations.increment(key)).thenReturn(1L); // first hit ever

        rateLimitService.enforce("login", "test@example.com", 5, Duration.ofMinutes(15));

        verify(redisTemplate).expire(key, Duration.ofMinutes(15));
    }

    @Test
    void enforce_belowLimit_shouldNotThrowAndShouldNotResetExpiry() {
        String key = "rate_limit:login:test@example.com";
        when(valueOperations.increment(key)).thenReturn(3L);

        rateLimitService.enforce("login", "test@example.com", 5, Duration.ofMinutes(15));


        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void enforce_exceedsLimit_shouldThrowRateLimitExceededException() {
        String key = "rate_limit:login:test@example.com";
        when(valueOperations.increment(key)).thenReturn(6L);
        when(redisTemplate.getExpire(key)).thenReturn(600L);

        assertThatThrownBy(() ->
                rateLimitService.enforce("login", "test@example.com", 5, Duration.ofMinutes(15))
        ).isInstanceOf(RateLimitExceededException.class)
         .hasMessageContaining("Too many attempts");
    }

    @Test
    void enforce_identifierIsCaseInsensitive() {

        String expectedKey = "rate_limit:login:test@example.com";
        when(valueOperations.increment(expectedKey)).thenReturn(1L);

        rateLimitService.enforce("login", "Test@Example.com", 5, Duration.ofMinutes(15));

        verify(valueOperations).increment(expectedKey);
    }


    @Test
    void assertNotBlocked_underLimit_shouldNotThrow() {
        String key = "rate_limit:login:test@example.com";
        when(valueOperations.get(key)).thenReturn("3");

        rateLimitService.assertNotBlocked("login", "test@example.com", 5);

    }

    @Test
    void assertNotBlocked_atLimit_shouldThrow() {
        String key = "rate_limit:login:test@example.com";
        when(valueOperations.get(key)).thenReturn("5");
        when(redisTemplate.getExpire(key)).thenReturn(300L);

        assertThatThrownBy(() ->
                rateLimitService.assertNotBlocked("login", "test@example.com", 5)
        ).isInstanceOf(RateLimitExceededException.class)
         .hasMessageContaining("Too many failed attempts");
    }

    @Test
    void assertNotBlocked_noPriorAttempts_shouldNotThrow() {
        String key = "rate_limit:login:newuser@example.com";
        when(valueOperations.get(key)).thenReturn(null);

        rateLimitService.assertNotBlocked("login", "newuser@example.com", 5);
    }

    @Test
    void assertNotBlocked_shouldNeverIncrementTheCounter() {

        String key = "rate_limit:login:test@example.com";
        when(valueOperations.get(key)).thenReturn("2");

        rateLimitService.assertNotBlocked("login", "test@example.com", 5);

        verify(valueOperations, never()).increment(anyString());
    }



    @Test
    void recordFailure_firstFailure_shouldSetExpiry() {
        String key = "rate_limit:login:test@example.com";
        when(valueOperations.increment(key)).thenReturn(1L);

        rateLimitService.recordFailure("login", "test@example.com", Duration.ofMinutes(15));

        verify(redisTemplate).expire(key, Duration.ofMinutes(15));
    }

    @Test
    void recordFailure_subsequentFailure_shouldNotResetExpiry() {
        String key = "rate_limit:login:test@example.com";
        when(valueOperations.increment(key)).thenReturn(2L);

        rateLimitService.recordFailure("login", "test@example.com", Duration.ofMinutes(15));

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }



    @Test
    void clearAttempts_shouldDeleteTheCorrectKey() {
        rateLimitService.clearAttempts("login", "test@example.com");

        verify(redisTemplate).delete("rate_limit:login:test@example.com");
    }
}