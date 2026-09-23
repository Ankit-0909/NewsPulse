package com.app.newsapp.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "feed_health")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sourceName;

    @Column(nullable = false)
    private LocalDateTime lastFetchedAt;

    @Column(nullable = false)
    private Long responseTimeMs;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String failureReason;
}