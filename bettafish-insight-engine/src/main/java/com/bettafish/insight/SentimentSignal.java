package com.bettafish.insight;

public record SentimentSignal(
    String label,
    double confidence,
    boolean enabled
) {
}
