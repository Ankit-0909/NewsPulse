package com.app.newsapp.service;

import com.app.newsapp.model.Article;
import com.app.newsapp.repository.ArticleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class AiSummarizationService {

    @Autowired
    private ArticleRepository articleRepository;

    @Value("${groq.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public void generateAndSaveSummary(Article article) {
        if (article.getOriginalContent() == null || article.getOriginalContent().trim().isEmpty() || article.getOriginalContent().length() < 10) {
            return;
        }

        try {
            String urlStr = "https://api.groq.com/openai/v1/chat/completions";

            String cleanContent = article.getOriginalContent().replaceAll("<[^>]*>", "").trim();
            if (cleanContent.length() > 1000) {
                cleanContent = cleanContent.substring(0, 1000);
            }

            Map<String, Object> systemMessage = Map.of(
                    "role", "system",
                    "content", "You are a precise tech editor. Summarize the text into exactly 3 short bullet points."
            );

            Map<String, Object> userMessage = Map.of(
                    "role", "user",
                    "content", cleanContent
            );

            Map<String, Object> payloadMap = Map.of(
                    "model", "openai/gpt-oss-120b",
                    "messages", List.of(systemMessage, userMessage)
            );

            String jsonPayload = objectMapper.writeValueAsString(payloadMap);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    Map responseMap = objectMapper.readValue(response.toString(), Map.class);
                    List<?> choices = (List<?>) responseMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
                        Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
                        String summary = (String) message.get("content");

                        if (summary != null) {
                            articleRepository.updateAiSummary(article.getId(), summary);
                            System.out.println("⚡ Groq Summary Generated & Saved for ID [" + article.getId() + "]");
                        }
                    }
                }
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line.trim());
                    }
                    System.err.println("❌ Groq Error Code: " + code + " | Details: " + errorResponse.toString());
                }
            }

        } catch (Exception e) {
            System.err.println("Groq API Error for Article ID " + article.getId() + ": " + e.getMessage());
        }
    }
}