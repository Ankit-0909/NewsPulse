package com.app.newsapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // 🔥 Yeh import add karo
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        sendSecureMail(toEmail,
                "📰 SyncRail News - Verify Your Account",
                "Verify Your Account", // Heading alag se bhej rahe hain taaki split ka crash risk khatam ho
                "Thank you for registering. Use the following security code to complete your verification pipeline.",
                otp);
    }

    @Async
    public void sendForgotPasswordOtp(String toEmail, String otp) {
        sendSecureMail(toEmail,
                "🔒 SyncRail News - Password Reset Request",
                "Password Reset Request", // Heading explicit pass kar di
                "We received a request to reset your password. Use the secure verification code below to set a new password.",
                otp);
    }

    private void sendSecureMail(String toEmail, String subject, String headerTitle, String messageText, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject(subject);

            helper.setFrom(senderEmail, "SyncRail News Team");

            String htmlContent = "<div style='font-family: Arial, sans-serif; border: 1px solid #e2e8f0; padding: 25px; border-radius: 8px; max-width: 480px; margin: 0 auto; color: #1e293b; background-color: #ffffff;'>"
                    + "<h2 style='color: #2563eb; margin-top: 0; font-size: 20px;'>" + headerTitle + "</h2>"
                    + "<p style='color: #475569; font-size: 14px; line-height: 1.6;'>" + messageText + "</p>"
                    + "<div style='background: #f8fafc; border: 1px dashed #cbd5e1; padding: 15px; border-radius: 6px; text-align: center; margin: 20px 0;'>"
                    + "<span style='font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #0f172a;'>" + otp + "</span>"
                    + "</div>"
                    + "<p style='font-size: 12px; color: #64748b;'>This code is strictly valid for <strong>3 minutes</strong>. If you did not request this operation, you can safely ignore this email.</p>"
                    + "<hr style='border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;'>"
                    + "<p style='font-size: 11px; color: #94a3b8; text-align: center;'>SyncRail News Aggregator Engine • Localhost Dev</p>"
                    + "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transmit security email pipeline: " + e.getMessage());
        }
    }
}