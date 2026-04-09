package com.bettafish.spider.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.bettafish.spider.model.CrawlConfig;
import com.bettafish.spider.model.CrawlResult;

@Component
public class WeiboCrawler implements PlatformCrawler {

    private static final Logger log = LoggerFactory.getLogger(WeiboCrawler.class);

    @Override
    public String platformName() {
        return "weibo";
    }

    @Override
    public List<CrawlResult> crawl(String query, List<String> keywords, CrawlConfig config) {
        log.info("Crawling Weibo for query='{}', keywords={}, maxResults={}", query, keywords, config.maxResults());
        return List.of(new CrawlResult(
            "weibo", "post", query + " 相关微博",
            "微博用户对「" + query + "」的讨论和评论内容",
            "微博用户", "https://weibo.com/detail/placeholder",
            Instant.now(), Map.of("like", 0, "comment", 0, "share", 0)
        ));
    }
}
