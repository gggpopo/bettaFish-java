package com.bettafish.sentiment.api;

public record SentimentAnalysisResponse(
    String label,
    double confidence
) {
}
