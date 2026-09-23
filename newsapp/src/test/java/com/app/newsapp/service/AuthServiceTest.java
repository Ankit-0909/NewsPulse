package com.app.newsapp.service;

import com.app.newsapp.dto.AuthResponse;
import com.app.newsapp.model.AuthProvider;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.UserRepository;
import com.app.newsapp.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private AuthenticationManager authenticationManager;
    private JwtUtils jwtUtils;
    private RefreshTokenService refreshTokenService;
    private RateLimitService rateLimitService;
    private AuthService authService;


    private static final String STRONG_PASSWORD = "StrongP@ss123";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtUtils = mock(JwtUtils.class);
        refreshTokenService = mock(RefreshTokenService.class);
        rateLimitService = mock(RateLimitService.class);

        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userRepository", userRepository);
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authService, "emailService", emailService);
        ReflectionTestUtils.setField(authService, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(authService, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(authService, "refreshTokenService", refreshTokenService);
        ReflectionTestUtils.setField(authService, "rateLimitService", rateLimitService);
    }

    private User buildUser(String email, boolean enabled) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test User");
        user.setPassword("hashed-password");
        user.setRole("ROLE_USER");
        user.setEnabled(enabled);
        user.setProvider(AuthProvider.LOCAL);
        return user;
    }



    @Test
    void register_weakPassword_shouldThrowWithoutTouchingDbOrEmail() {
        assertThatThrownBy(() ->
                authService.registerLocalUser("user@example.com", "weak", "Test User")
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Password policy failed");


        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void register_newUser_shouldSaveDisabledAndSendOtp() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        authService.registerLocalUser("new@example.com", STRONG_PASSWORD, "New User");

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("new@example.com")
                        && !user.isEnabled()
                        && user.getRole().equals("ROLE_USER")
                        && user.getOtpCode() != null
        ));
        verify(emailService).sendOtpEmail(eq("new@example.com"), anyString());
    }

    @Test
    void register_existingAndAlreadyVerified_shouldThrow() {
        User existing = buildUser("taken@example.com", true);
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                authService.registerLocalUser("taken@example.com", STRONG_PASSWORD, "Someone")
        ).hasMessageContaining("already registered and verified");

        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void register_existingButUnverified_shouldOverwriteAndResendOtp() {

        User existing = buildUser("pending@example.com", false);
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(existing));

        authService.registerLocalUser("pending@example.com", STRONG_PASSWORD, "Updated Name");

        verify(userRepository).save(argThat(user -> user.getName().equals("Updated Name")));
        verify(emailService).sendOtpEmail(eq("pending@example.com"), anyString());
    }



    @Test
    void verifyOtp_correctCode_shouldEnableAccount() {
        User user = buildUser("user@example.com", false);
        user.setOtpCode("1234");
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        authService.verifyOtp("user@example.com", "1234");

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getOtpCode()).isNull();
    }

    @Test
    void verifyOtp_alreadyVerified_shouldShortCircuitWithoutCheckingCode() {
        User user = buildUser("user@example.com", true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        String result = authService.verifyOtp("user@example.com", "wrong-but-irrelevant");

        assertThat(result).contains("already verified");
    }

    @Test
    void verifyOtp_expiredCode_shouldThrow() {
        User user = buildUser("user@example.com", false);
        user.setOtpCode("1234");
        user.setOtpExpiryTime(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "1234"))
                .hasMessageContaining("expired");
    }

    @Test
    void verifyOtp_wrongCode_shouldThrow() {
        User user = buildUser("user@example.com", false);
        user.setOtpCode("1234");
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyOtp("user@example.com", "9999"))
                .hasMessageContaining("Invalid code");
    }



    @Test
    void login_success_shouldReturnTokensAndClearRateLimitAttempts() {
        User user = buildUser("user@example.com", true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(jwtUtils.generateToken("user@example.com", "ROLE_USER")).thenReturn("fake-access-token");
        when(refreshTokenService.createRefreshToken("user@example.com")).thenReturn("fake-refresh-token");

        AuthResponse response = authService.loginLocalUser("user@example.com", STRONG_PASSWORD);

        assertThat(response.getAccessToken()).isEqualTo("fake-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("fake-refresh-token");
        verify(rateLimitService).clearAttempts("login", "user@example.com");
        verify(rateLimitService, never()).recordFailure(anyString(), anyString(), any());
    }

    @Test
    void login_accountDoesNotExist_shouldRecordFailureAndReturnGenericMessage() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());


        assertThatThrownBy(() -> authService.loginLocalUser("ghost@example.com", "whatever"))
                .hasMessage("Invalid email or password.");

        verify(rateLimitService).recordFailure("login", "ghost@example.com", Duration.ofMinutes(15));
    }

    @Test
    void login_wrongPassword_shouldRecordFailureAndReturnGenericMessage() {
        User user = buildUser("user@example.com", true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        assertThatThrownBy(() -> authService.loginLocalUser("user@example.com", "wrong-password"))
                .hasMessage("Invalid email or password.");

        verify(rateLimitService).recordFailure("login", "user@example.com", Duration.ofMinutes(15));
        verify(jwtUtils, never()).generateToken(anyString(), anyString());
    }

    @Test
    void login_unverifiedAccount_shouldThrowWithoutRecordingRateLimitFailure() {

        User user = buildUser("user@example.com", false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginLocalUser("user@example.com", STRONG_PASSWORD))
                .hasMessageContaining("unverified");

        verify(rateLimitService, never()).recordFailure(anyString(), anyString(), any());
        verify(authenticationManager, never()).authenticate(any());
    }



    @Test
    void forgotPassword_accountDoesNotExist_shouldReturnGenericMessageWithoutSendingEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        Map<String, String> result = authService.initiateForgotPassword("ghost@example.com");

        assertThat(result.get("message")).contains("If an account exists");
        verify(emailService, never()).sendForgotPasswordOtp(anyString(), anyString());
    }

    @Test
    void forgotPassword_realAccount_shouldSendEmailWithSameGenericMessage() {
        User user = buildUser("user@example.com", true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        Map<String, String> result = authService.initiateForgotPassword("user@example.com");


        assertThat(result.get("message")).contains("If an account exists");
        verify(emailService).sendForgotPasswordOtp(eq("user@example.com"), anyString());
    }


    @Test
    void resetPassword_correctOtp_shouldUpdatePasswordAndClearOtp() {
        User user = buildUser("user@example.com", true);
        user.setOtpCode("5678");
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(2));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("newly-hashed-password");

        authService.executeForgotPassword("user@example.com", "5678", STRONG_PASSWORD);

        assertThat(user.getPassword()).isEqualTo("newly-hashed-password");
        assertThat(user.getOtpCode()).isNull();
    }

    @Test
    void resetPassword_expiredOtp_shouldThrowAndLeavePasswordUnchanged() {
        User user = buildUser("user@example.com", true);
        user.setOtpCode("5678");
        user.setOtpExpiryTime(LocalDateTime.now().minusMinutes(1));
        String originalPassword = user.getPassword();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                authService.executeForgotPassword("user@example.com", "5678", STRONG_PASSWORD)
        ).hasMessageContaining("expired");

        assertThat(user.getPassword()).isEqualTo(originalPassword);
    }
}