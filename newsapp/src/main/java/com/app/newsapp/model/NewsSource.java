package com.app.newsapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "news_sources")
public class NewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sourceName;

    @Column(nullable = false)
    private String rssUrl;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private boolean isActive = true;


    public NewsSource() {}

    public NewsSource(String sourceName, String rssUrl, String category, boolean isActive) {
        this.sourceName = sourceName;
        this.rssUrl = rssUrl;
        this.category = category;
        this.isActive = isActive;
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getRssUrl() { return rssUrl; }
    public void setRssUrl(String rssUrl) { this.rssUrl = rssUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}