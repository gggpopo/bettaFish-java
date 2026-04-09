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
public class XiaohongshuCrawler implements PlatformCrawler {

    private static final Logger log = LoggerFactory.getLogger(XiaohongshuCrawler.class);

    @Override
    public String platformName() {
        return "xiaohongshu";
    }

    @Override
    public List<CrawlResult> crawl(String query, List<String> keywords, CrawlConfig config) {
        log.info("Crawling Xiaohongshu for query='{}', keywords={}, maxResults={}", query, keywords, config.maxResults());
        return List.of(new CrawlResult(
            "xhs", "note", query + " 相关笔记",
            "小红书用户对「" + query + "」的讨论和评论内容",
            "小红书用户", "https://xiaohongshu.com/explore/placeholder",
            Instant.now(), Map.of("like", 0, "comment", 0, "collect", 0)
        ));
    }
}
