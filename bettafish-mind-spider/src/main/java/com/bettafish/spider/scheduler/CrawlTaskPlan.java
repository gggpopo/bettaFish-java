package com.bettafish.spider.scheduler;

import java.util.List;

public record CrawlTaskPlan(
    String query,
    List<String> targetPlatforms
) {
}
