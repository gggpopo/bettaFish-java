package com.bettafish.insight.tool;

import org.springframework.stereotype.Component;
import com.bettafish.insight.SentimentAnalysisClient;
import com.bettafish.insight.SentimentSignal;

@Component
public class SentimentTool {

    private final SentimentAnalysisClient sentimentAnalysisClient;

    public SentimentTool(SentimentAnalysisClient sentimentAnalysisClient) {
        this.sentimentAnalysisClient = sentimentAnalysisClient;
    }

    public SentimentSignal analyze(String text) {
        return sentimentAnalysisClient.analyze(text);
    }
}
