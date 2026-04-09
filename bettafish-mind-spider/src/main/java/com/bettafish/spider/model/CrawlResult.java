package com.bettafish.spider.model;

import java.time.Instant;
import java.util.Map;

public record CrawlResult(
    String platform,
    String contentType,
    String title,
    String content,
    String author,
    String url,
    Instant publishTime,
    Map<String, Integer> engagement
) {}
