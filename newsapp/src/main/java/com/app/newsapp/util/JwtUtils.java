package com.app.newsapp.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${newsapp.jwt.secret}")
    private String jwtSecret;

    @Value("${newsapp.jwt.expiration}")
    private long jwtExpirationMs;

    // 🔒 FIX (jjwt 0.11.5 -> 0.12.6 migration): return type ab SecretKey hai
    // (pehle generic Key tha) — 0.12.x ka verifyWith() specifically SecretKey
    // maangta hai, generic Key nahi accept karta.
    private SecretKey getSigningKey() {
        byte[] keyBytes = this.jwtSecret.getBytes(StandardCharsets.UTF_8);
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
    }

    // 🪙 1a. Purana overload — sirf email se token banta hai (backward-compatible)
    // 🔒 FIX: .setSubject()/.setIssuedAt()/.setExpiration() (0.11.x style) ->
    // .subject()/.issuedAt()/.expiration() (0.12.x fluent style)
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // 🪙 1b. Role ke saath token banane ka overload — login ke liye use hota hai
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // 📧 2. Token se User Email nikalne ka logic
    public String getEmailFromJwtToken(String token) {
        return getAllClaims(token).getSubject();
    }

    // 3. Token se role nikalne ka logic
    public String getRoleFromJwtToken(String token) {
        Object role = getAllClaims(token).get("role");
        return role != null ? role.toString() : null;
    }

    // 🔒 FIX: Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody()
    // (0.11.x) -> Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload() (0.12.x)
    private Claims getAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 🛡️ 4. Token Validate karne ka logic
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (Exception e) {
            System.err.println("❌ JWT Validation Failed: " + e.getMessage());
        }
        return false;
    }
}