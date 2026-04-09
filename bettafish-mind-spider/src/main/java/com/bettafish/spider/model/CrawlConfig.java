package com.bettafish.spider.model;

import java.time.Duration;
import java.time.LocalDate;

public record CrawlConfig(
    int maxResults,
    Duration timeout,
    LocalDate startDate,
    LocalDate endDate
) {
    public static CrawlConfig defaults() {
        return new CrawlConfig(50, Duration.ofSeconds(30), null, null);
    }
}
