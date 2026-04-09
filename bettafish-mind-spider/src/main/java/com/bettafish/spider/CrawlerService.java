package com.bettafish.spider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.bettafish.spider.model.CrawlConfig;
import com.bettafish.spider.model.CrawlResult;
import com.bettafish.spider.platform.PlatformCrawler;
import com.bettafish.spider.scheduler.CrawlTaskPlan;
import com.bettafish.spider.topic.TopicExtractor;
import com.bettafish.spider.topic.TopicExtractor.ExtractedTopic;

@Service
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    private final TopicExtractor topicExtractor;
    private final Map<String, PlatformCrawler> crawlerMap;

    public CrawlerService(TopicExtractor topicExtractor, List<PlatformCrawler> platformCrawlers) {
        this.topicExtractor = topicExtractor;
        this.crawlerMap = platformCrawlers.stream()
            .collect(Collectors.toMap(PlatformCrawler::platformName, Function.identity()));
    }

    public CrawlTaskPlan planCapture(String query) {
        return new CrawlTaskPlan(
            query,
            List.copyOf(crawlerMap.keySet()),
            List.of(),
            CrawlConfig.defaults()
        );
    }

    public List<CrawlResult> crawl(String query) {
        log.info("Starting full crawl pipeline for query='{}'", query);

        // Stage 1: Extract topics and keywords via LLM
        List<ExtractedTopic> topics = topicExtractor.extract(query, 5);
        List<String> keywords = topics.stream()
            .flatMap(t -> t.keywords().stream())
            .distinct()
            .toList();
        log.info("Extracted {} topics with {} keywords", topics.size(), keywords.size());

        // Stage 2: Build crawl plan
        CrawlConfig config = CrawlConfig.defaults();
        CrawlTaskPlan plan = new CrawlTaskPlan(query, List.copyOf(crawlerMap.keySet()), keywords, config);
        log.info("Crawl plan: platforms={}, keywords={}", plan.platforms(), plan.keywords());

        // Stage 3: Dispatch to platform crawlers and aggregate
        List<CrawlResult> results = new ArrayList<>();
        for (String platform : plan.platforms()) {
            PlatformCrawler crawler = crawlerMap.get(platform);
            if (crawler != null) {
                try {
                    List<CrawlResult> platformResults = crawler.crawl(query, keywords, config);
                    results.addAll(platformResults);
                    log.info("Platform '{}' returned {} results", platform, platformResults.size());
                } catch (Exception e) {
                    log.error("Crawl failed for platform '{}': {}", platform, e.getMessage(), e);
                }
            }
        }

        log.info("Full crawl pipeline completed: {} total results from {} platforms", results.size(), plan.platforms().size());
        return results;
    }
}
