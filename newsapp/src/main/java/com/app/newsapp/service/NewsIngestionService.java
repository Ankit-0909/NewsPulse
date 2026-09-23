package com.app.newsapp.service;

import com.app.newsapp.model.Article;
import com.app.newsapp.model.FeedHealth;
import com.app.newsapp.repository.ArticleRepository;
import com.app.newsapp.repository.FeedHealthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.jsoup.Jsoup;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;

@Service
public class NewsIngestionService {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private AiSummarizationService aiSummarizationService;

    @Autowired
    private ArticleCacheService articleCacheService;

    @Autowired
    private FeedHealthRepository feedHealthRepository;

    @Async("newsTaskExecutor")
    public void fetchAndProcessSingleCategory(String sourceName, String category, String rssUrl) {
        String currentThread = Thread.currentThread().getName();
        System.out.println("📥 [" + currentThread + "] Parsing Started for Category: [" + category + "]");

        long startTime = System.currentTimeMillis();


        FeedHealth health = feedHealthRepository.findBySourceName(sourceName)
                .orElse(new FeedHealth());
        health.setSourceName(sourceName);


        int savedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        String lastArticleError = null;

        try {
            URL url = new URL(rssUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();


            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(connection.getInputStream());
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("item");
            int limit = Math.min(nodeList.getLength(), 3);
            boolean newArticleSaved = false;

            for (int i = 0; i < limit; i++) {

                try {
                    Element element = (Element) nodeList.item(i);

                    String title = getTagValue("title", element);
                    String description = getTagValue("description", element);
                    String link = getTagValue("link", element);

                    if (title == null || articleRepository.existsByTitle(title)) {
                        skippedCount++;
                        continue;
                    }

                    String cleanContent = cleanDescription(description);
                    if (cleanContent == null || cleanContent.isBlank()) {
                        System.out.println("⏭️ Skipping article because content is missing: " + title);
                        skippedCount++;
                        continue;
                    }

                    Article article = new Article();
                    article.setTitle(title);
                    article.setOriginalContent(cleanContent);
                    article.setContentUrl(link != null ? link : rssUrl);
                    article.setSourceName(sourceName);
                    article.setCategory(category);
                    article.setPublishedAt(LocalDateTime.now());

                    Article savedArticle = articleRepository.save(article);

                    newArticleSaved = true;
                    savedCount++;
                    System.out.println("✅ [" + currentThread + "] Saved to [" + category + "]: " + savedArticle.getTitle());

                    aiSummarizationService.generateAndSaveSummary(savedArticle);

                } catch (Exception articleError) {

                    failedCount++;
                    lastArticleError = articleError.getMessage() != null
                            ? articleError.getMessage()
                            : articleError.getClass().getSimpleName();
                    System.err.println("⚠️ [" + currentThread + "] Skipping one bad article in [" + category + "]: " + lastArticleError);
                }
            }

            if (newArticleSaved) {
                articleCacheService.clearFeedCaches();
            }

            long endTime = System.currentTimeMillis();
            health.setResponseTimeMs(endTime - startTime);


            health.setStatus("SUCCESS");
            health.setLastFetchedAt(LocalDateTime.now());
            health.setFailureReason(
                    failedCount > 0
                            ? failedCount + " article(s) skipped due to errors (e.g. " + lastArticleError + ")"
                            : null
            );

            System.out.println("🏁 [" + currentThread + "] Finished [" + category + "] — saved: " + savedCount
                    + ", skipped: " + skippedCount + ", failed: " + failedCount);

        } catch (Exception e) {

            long endTime = System.currentTimeMillis();
            health.setResponseTimeMs(endTime - startTime);
            health.setStatus("FAILURE");
            health.setLastFetchedAt(LocalDateTime.now());
            health.setFailureReason(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());

            System.err.println("❌ [" + currentThread + "] Network/Parsing Alert for Category [" + category + "]: " + e.getMessage());
        } finally {
            feedHealthRepository.save(health);
        }
    }

    private String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        String cleanText = Jsoup.parse(description).text().trim();

        if (cleanText.isBlank()) {
            return null;
        }

        return cleanText;
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}