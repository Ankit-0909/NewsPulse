package com.app.newsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;


    @JsonIgnore
    private String password;

    @Column(nullable = false)
    private String role="ROLE_USER";

    @Column(name = "is_enabled")
    private boolean isEnabled = false;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuthProvider provider;



    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_subscribed_categories", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "category")
    private Set<String> subscribedCategories = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_subscribed_sources", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "source_name")
    private Set<String> subscribedSources = new HashSet<>();




    @JsonIgnore
    @Column(name = "otp_code", length = 4)
    private String otpCode;

    @JsonIgnore
    @Column(name = "otp_expiry_time")
    private LocalDateTime otpExpiryTime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}