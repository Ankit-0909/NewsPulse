package com.app.newsapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;


    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "\\d{4}", message = "OTP must be exactly 4 digits")
    private String otp;
}