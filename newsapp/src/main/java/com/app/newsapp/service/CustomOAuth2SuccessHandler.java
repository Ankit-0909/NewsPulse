package com.app.newsapp.service;

import com.app.newsapp.model.User;
import com.app.newsapp.repository.UserRepository;
import com.app.newsapp.util.JwtUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class CustomOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String FRONTEND_REDIRECT_URL = "http://localhost:5500/feed.html";

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthCodeService oAuthCodeService;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IOException("OAuth user record not found after authentication."));

        String accessToken = jwtUtils.generateToken(email, user.getRole());
        String refreshTokenStr = refreshTokenService.createRefreshToken(email);

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshTokenStr)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        String code = oAuthCodeService.issueCode(accessToken, refreshTokenStr, email);
        String redirectUrl = FRONTEND_REDIRECT_URL + "?code=" + code;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}