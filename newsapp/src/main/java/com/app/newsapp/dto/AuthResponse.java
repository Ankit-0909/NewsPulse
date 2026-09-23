package com.app.newsapp.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String email;
    private String name;
    private String role;
    private String message;


    public AuthResponse(String accessToken, String refreshToken, String email, String name, String role) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.email = email;
        this.name = name;
        this.role = role;
    }


    public AuthResponse(String accessToken, String message) {
        this.accessToken = accessToken;
        this.message = message;
    }
}