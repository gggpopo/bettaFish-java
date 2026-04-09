package com.bettafish.insight.tool;

import java.time.Instant;
import java.util.Map;

/**
 * Unified query result from the MediaCrawler database.
 * Represents a single piece of content or comment from any platform.
 */
public record QueryResult(
    String platform,
    String contentType,
    String titleOrContent,
    String authorNickname,
    String url,
    Instant publishTime,
    Map<String, Integer> engagement,
    double hotnessScore,
    String sourceKeyword,
    String sourceTable
) {}
