package com.app.newsapp.service;

import com.app.newsapp.dto.AuthResponse;
import com.app.newsapp.model.AuthProvider;
import com.app.newsapp.model.User;
import com.app.newsapp.repository.UserRepository;
import com.app.newsapp.util.JwtUtils;
import com.app.newsapp.util.PasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RateLimitService rateLimitService;

    public String registerLocalUser(String email, String password, String name) {
        if (!PasswordValidator.isValid(password)) {
            throw new RuntimeException("Password policy failed: Must be at least 8 characters long, contain uppercase, lowercase, a number, and a special character.");
        }

        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        User user;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (user.isEnabled()) {
                throw new RuntimeException("This email address is already registered and verified!");
            }
            user.setName(name);
            user.setPassword(passwordEncoder.encode(password));
        } else {
            user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setPassword(passwordEncoder.encode(password));
            user.setProvider(AuthProvider.LOCAL);

            user.setRole("ROLE_USER");
        }

        user.setEnabled(false);

        String otp = String.valueOf(new Random().nextInt(9000) + 1000);
        user.setOtpCode(otp);

        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(3));

        userRepository.save(user);

        emailService.sendOtpEmail(email, otp);


        return "Registration initiated! Please enter the 4-digit verification code sent to your email.";
    }

    public String verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Verification failed: User email records not found!"));

        if (user.isEnabled()) {
            return "Account is already verified and active.";
        }

        if (user.getOtpExpiryTime() == null || LocalDateTime.now().isAfter(user.getOtpExpiryTime())) {
            throw new RuntimeException("The OTP code has expired! Please register again to generate a new code.");
        }

        if (user.getOtpCode() == null || !user.getOtpCode().equals(otp)) {
            throw new RuntimeException("Invalid code! Please check your code and try again.");
        }

        user.setEnabled(true);
        user.setOtpCode(null);
        user.setOtpExpiryTime(null);
        userRepository.save(user);

        return "Account successfully verified! You can now proceed to log in.";
    }

    public AuthResponse loginLocalUser(String email, String password) {

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            rateLimitService.recordFailure("login", email, Duration.ofMinutes(15));
            throw new RuntimeException("Invalid email or password.");
        }

        if (!user.isEnabled()) {

            throw new RuntimeException("Your account is unverified! Please complete the OTP registration process first.");
        }


        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (Exception e) {

            rateLimitService.recordFailure("login", email, Duration.ofMinutes(15));
            throw new RuntimeException("Invalid email or password.");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);

        rateLimitService.clearAttempts("login", email);


        String accessToken = jwtUtils.generateToken(email, user.getRole());
        String refreshTokenStr = refreshTokenService.createRefreshToken(email);

        return new AuthResponse(accessToken, refreshTokenStr, user.getEmail(), user.getName(), user.getRole());
    }


    public Map<String, String> initiateForgotPassword(String email) {

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty() || !userOptional.get().isEnabled()) {
            return Map.of("message", "If an account exists for this email, a recovery code has been sent.");
        }

        User user = userOptional.get();


        String otp = String.valueOf(new Random().nextInt(9000) + 1000);
        user.setOtpCode(otp);
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(3)); // 3-minute validation window

        userRepository.save(user);

        emailService.sendForgotPasswordOtp(email, otp);

        return Map.of("message", "If an account exists for this email, a recovery code has been sent.");
    }

    public Map<String, String> executeForgotPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account records missing for this identity."));


        if (user.getOtpExpiryTime() == null || LocalDateTime.now().isAfter(user.getOtpExpiryTime())) {
            throw new RuntimeException("The verification code has expired! Please request a new code.");
        }


        if (user.getOtpCode() == null || !user.getOtpCode().equals(otp)) {
            throw new RuntimeException("Invalid verification code! Please check your code and try again.");
        }


        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtpCode(null);
        user.setOtpExpiryTime(null);

        userRepository.save(user);

        return Map.of("message", "Password updated successfully! You can now log in with your new credentials.");
    }
}