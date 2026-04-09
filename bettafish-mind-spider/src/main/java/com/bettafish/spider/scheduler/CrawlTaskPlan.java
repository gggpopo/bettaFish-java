package com.bettafish.spider.scheduler;

import java.util.List;
import com.bettafish.spider.model.CrawlConfig;

public record CrawlTaskPlan(
    String query,
    List<String> platforms,
    List<String> keywords,
    CrawlConfig config
) {}
