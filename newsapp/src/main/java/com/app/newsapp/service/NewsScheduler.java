//package com.app.newsapp.service;
//
//import com.app.newsapp.model.NewsSource;
//import com.app.newsapp.repository.NewsSourceRepository;
//import com.app.newsapp.service.NewsIngestionService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.cache.annotation.CacheEvict;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//@Component
//public class NewsScheduler {
//
//    @Autowired
//    private NewsIngestionService newsIngestionService;
//
//    @Autowired
//    private NewsSourceRepository newsSourceRepository;
//
//  //  private final Map<String, String> categoryFeeds = new LinkedHashMap<>();
//
////    public NewsScheduler() {
////        categoryFeeds.put("India", "https://timesofindia.indiatimes.com/rssfeeds/-2128936835.cms");
////        categoryFeeds.put("Business", "https://timesofindia.indiatimes.com/rssfeeds/1898055.cms");
////        categoryFeeds.put("Tech", "https://techcrunch.com/feed/");
////        categoryFeeds.put("Sports", "https://www.espncricinfo.com/rss/content/story/feeds/0.xml");
////        categoryFeeds.put("Entertainment", "https://timesofindia.indiatimes.com/rssfeeds/1081479906.cms");
////        categoryFeeds.put("Education", "https://timesofindia.indiatimes.com/rssfeeds/913168846.cms");
////        categoryFeeds.put("Life and style", "https://timesofindia.indiatimes.com/rssfeeds/2886704.cms");
////    }
//
//    // 🔥 Har 15 minute mein automatic chalega (Aur naya data aane se pehle Redis Cache saaf kar dega)
//    @Scheduled(fixedRate = 120000)
//    @CacheEvict(value = {"newsCache", "singleArticleCache"}, allEntries = true)
//    public void triggerComprehensiveNewsIngestion() {
//        System.out.println("⏰ [SCHEDULER] Background Ingestion Cycle Started at: " + LocalDateTime.now());
//
//        // Har category ke liye alag async thread trigger ho jayega bina main process ko block kiye
////        categoryFeeds.forEach((category, url) -> {
////            newsIngestionService.fetchAndProcessSingleCategory(category, url);
////        });
//
//        List<NewsSource> activeSources = newsSourceRepository.findByIsActiveTrue();
//
//        if (activeSources.isEmpty()) {
//            System.out.println("⚠️ [SCHEDULER] No active news sources found in the database!");
//            return;
//        }
//
//        // Saare active sources ko ek-ek karke async thread mein bhej do
//        activeSources.forEach(source -> {
//            // Ab hum Source Name, Category, aur URL teeno cheezein dynamic bhej rahe hain
//            newsIngestionService.fetchAndProcessSingleCategory(
//                    source.getSourceName(),
//                    source.getCategory(),
//                    source.getRssUrl()
//            );
//        });
//
//        System.out.println("🚀 [SCHEDULER] All " + activeSources.size() + " active database sources delegated to separate threads!");
//
//      //  System.out.println("🚀 [SCHEDULER] All 7 categories successfully delegated to separate execution threads!");
//    }
//}
package com.app.newsapp.service;

import com.app.newsapp.model.NewsSource;
import com.app.newsapp.repository.NewsSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class NewsScheduler {

    @Autowired
    private NewsIngestionService newsIngestionService;

    @Autowired
    private NewsSourceRepository newsSourceRepository;



    @CacheEvict(value = {"newsCache", "singleArticleCache", "categoryNewsCache"}, allEntries = true)
    public void triggerComprehensiveNewsIngestion() {
        System.out.println("📡 [MANUAL TRIGGER] Ingestion Cycle Started at: " + LocalDateTime.now());

        List<NewsSource> activeSources = newsSourceRepository.findByIsActiveTrue();

        if (activeSources.isEmpty()) {
            System.out.println("⚠️ [MANUAL TRIGGER] No active news sources found in the database!");
            return;
        }

        activeSources.forEach(source -> {
            newsIngestionService.fetchAndProcessSingleCategory(
                    source.getSourceName(),
                    source.getCategory(),
                    source.getRssUrl()
            );
        });

        System.out.println("🚀 [MANUAL TRIGGER] All " + activeSources.size() + " active database sources delegated to separate threads!");
    }
}

