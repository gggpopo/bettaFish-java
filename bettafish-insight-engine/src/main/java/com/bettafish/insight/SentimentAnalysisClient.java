package com.bettafish.insight;

import java.util.List;

public interface SentimentAnalysisClient {

    SentimentSignal analyze(String text);

    default List<SentimentSignal> analyzeBatch(List<String> texts) {
        return texts.stream().map(this::analyze).toList();
    }
}
