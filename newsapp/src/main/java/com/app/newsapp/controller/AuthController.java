package com.app.newsapp.controller;

import com.app.newsapp.dto.*;
import com.app.newsapp.model.RefreshToken;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.UserRepository;
import com.app.newsapp.service.AuthService;
import com.app.newsapp.service.OAuthCodeService;
import com.app.newsapp.service.RateLimitService;
import com.app.newsapp.service.RefreshTokenService;
import com.app.newsapp.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private OAuthCodeService oAuthCodeService;

    @Autowired
    private UserRepository userRepository;


    private boolean cookieSecure;


    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        rateLimitService.enforce("register", request.getEmail(), 3, Duration.ofMinutes(10));
        try {
            String response = authService.registerLocalUser(
                    request.getEmail(),
                    request.getPassword(),
                    request.getName()
            );
            return ResponseEntity.ok(Map.of("message", response));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }


    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        rateLimitService.enforce("verify-otp", request.getEmail(), 5, Duration.ofMinutes(3));
        try {
            String response = authService.verifyOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        rateLimitService.assertNotBlocked("login", request.getEmail(), 5);
        try {
            AuthResponse authResponse = authService.loginLocalUser(request.getEmail(), request.getPassword());

            ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", authResponse.getRefreshToken())
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path("/")
                    .maxAge(7 * 24 * 60 * 60)
                    .sameSite("Lax")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Authentication failed: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(HttpServletRequest request) {
        String requestRefreshToken = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    requestRefreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (requestRefreshToken == null || requestRefreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body("Refresh Token is missing in cookies!");
        }

        Optional<User> userOptional = refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(com.app.newsapp.model.RefreshToken::getUser);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            String newAccessToken = jwtUtils.generateToken(user.getEmail(), user.getRole());

            return ResponseEntity.ok(new AuthResponse(newAccessToken, "Access token refreshed successfully!"));
        }

        return ResponseEntity.badRequest().body("Refresh token is invalid or expired!");
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<?> exchangeOAuthCode(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing code."));
        }

        return oAuthCodeService.redeemCode(code)
              //  .map(payload -> {
                .<ResponseEntity<?>>map(payload -> {
                    String email = payload.get("email");
                    User user = userRepository.findByEmail(email).orElse(null);
                    String role = user != null ? user.getRole() : null;
                    String name = user != null ? user.getName() : null;

                    AuthResponse authResponse = new AuthResponse(
                            payload.get("accessToken"),
                            payload.get("refreshToken"),
                            email,
                            name,
                            role
                    );
                    return ResponseEntity.ok(authResponse);
                })
                .orElseGet(() -> ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid or expired code. Please try signing in again.")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request) {
        String refreshTokenFromCookie = null;

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshTokenFromCookie = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshTokenFromCookie != null) {
            refreshTokenService.revokeToken(refreshTokenFromCookie);
        }

        ResponseCookie cleanRefreshTokenCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanRefreshTokenCookie.toString())
                .body("Logged out successfully! Cookies cleared and session revoked.");
    }

    @PostMapping("/forgot-password/initiate")
    public ResponseEntity<?> initiateForgotPassword(
            @RequestParam
            @NotBlank(message = "Email is required")
            @Email(message = "Please enter a valid email address")
            String email) {
        rateLimitService.enforce("forgot-init", email, 3, Duration.ofMinutes(10));
        try {
            var response = authService.initiateForgotPassword(email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/execute")
    public ResponseEntity<?> executeForgotPassword(@Valid @RequestBody ResetPasswordRequest request) {
        rateLimitService.enforce("forgot-execute", request.getEmail(), 5, Duration.ofMinutes(3));
        try {
            var response = authService.executeForgotPassword(
                    request.getEmail(),
                    request.getOtp(),
                    request.getNewPassword()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}