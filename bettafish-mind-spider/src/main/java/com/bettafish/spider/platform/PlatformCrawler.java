package com.bettafish.spider.platform;

import java.util.List;
import com.bettafish.spider.model.CrawlConfig;
import com.bettafish.spider.model.CrawlResult;

public interface PlatformCrawler {

    String platformName();

    List<CrawlResult> crawl(String query, List<String> keywords, CrawlConfig config);
}
