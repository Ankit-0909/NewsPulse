package com.app.newsapp.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;


class JwtUtilsTest {

    private JwtUtils jwtUtils;


    private static final String TEST_SECRET = "this-is-a-test-secret-key-for-jwt-unit-tests-only-32bytes+";
    private static final long TEST_EXPIRATION_MS = 15 * 60 * 1000;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", TEST_EXPIRATION_MS);
    }

    @Test
    void generateToken_withEmailOnly_shouldEncodeCorrectSubject() {
        String token = jwtUtils.generateToken("user@example.com");

        assertThat(token).isNotBlank();
        assertThat(jwtUtils.getEmailFromJwtToken(token)).isEqualTo("user@example.com");
    }

    @Test
    void generateToken_withEmailAndRole_shouldEncodeBothClaims() {
        String token = jwtUtils.generateToken("admin@example.com", "ROLE_ADMIN");

        assertThat(jwtUtils.getEmailFromJwtToken(token)).isEqualTo("admin@example.com");
        assertThat(jwtUtils.getRoleFromJwtToken(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void generateToken_withEmailOnlyOverload_shouldHaveNullRole() {

        String token = jwtUtils.generateToken("user@example.com");

        assertThat(jwtUtils.getRoleFromJwtToken(token)).isNull();
    }

    @Test
    void validateJwtToken_withFreshlyGeneratedToken_shouldReturnTrue() {
        String token = jwtUtils.generateToken("user@example.com", "ROLE_USER");

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
    }

    @Test
    void validateJwtToken_withExpiredToken_shouldReturnFalse() {

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 20 * 60 * 1000))
                .expiration(new Date(System.currentTimeMillis() - 5 * 60 * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtUtils.validateJwtToken(expiredToken)).isFalse();
    }

    @Test
    void validateJwtToken_signedWithDifferentKey_shouldReturnFalse() {

        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-not-known-to-app".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .subject("attacker@example.com")
                .claim("role", "ROLE_ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TEST_EXPIRATION_MS))
                .signWith(wrongKey, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtUtils.validateJwtToken(forgedToken)).isFalse();
    }

    @Test
    void validateJwtToken_withMalformedString_shouldReturnFalse() {
        assertThat(jwtUtils.validateJwtToken("not.a.valid.jwt.token")).isFalse();
    }

    @Test
    void validateJwtToken_withEmptyString_shouldReturnFalse() {
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }

    @Test
    void getEmailFromJwtToken_shouldRoundTripCorrectly_forVariousEmailFormats() {
        String[] emails = {"a@b.com", "test.user+tag@example.co.in", "admin@syncrail.news"};

        for (String email : emails) {
            String token = jwtUtils.generateToken(email);
            assertThat(jwtUtils.getEmailFromJwtToken(token)).isEqualTo(email);
        }
    }
}