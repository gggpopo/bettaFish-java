package com.bettafish.insight.tool;

import java.util.List;
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

    public List<SentimentSignal> analyzeBatch(List<String> texts) {
        return sentimentAnalysisClient.analyzeBatch(texts);
    }
}
