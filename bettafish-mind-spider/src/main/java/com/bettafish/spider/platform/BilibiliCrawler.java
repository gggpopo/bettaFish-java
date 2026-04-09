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
public class BilibiliCrawler implements PlatformCrawler {

    private static final Logger log = LoggerFactory.getLogger(BilibiliCrawler.class);

    @Override
    public String platformName() {
        return "bilibili";
    }

    @Override
    public List<CrawlResult> crawl(String query, List<String> keywords, CrawlConfig config) {
        log.info("Crawling Bilibili for query='{}', keywords={}, maxResults={}", query, keywords, config.maxResults());
        return List.of(new CrawlResult(
            "bilibili", "video", query + " 相关视频",
            "B站用户对「" + query + "」的讨论和评论内容",
            "B站用户", "https://bilibili.com/video/placeholder",
            Instant.now(), Map.of("like", 0, "comment", 0, "share", 0, "view", 0, "danmaku", 0)
        ));
    }
}
