package com.bettafish.insight;

public interface SentimentAnalysisClient {

    SentimentSignal analyze(String text);
}
