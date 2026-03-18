package com.bettafish.sentiment;

import java.util.Locale;
import org.springframework.stereotype.Service;
import com.bettafish.sentiment.api.SentimentAnalysisResponse;

@Service
public class OnnxSentimentAnalyzer {

    public SentimentAnalysisResponse analyze(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (normalized.contains("喜欢") || normalized.contains("太棒") || normalized.contains("great")) {
            return new SentimentAnalysisResponse("POSITIVE", 0.85);
        }
        if (normalized.contains("讨厌") || normalized.contains("糟糕") || normalized.contains("bad")) {
            return new SentimentAnalysisResponse("NEGATIVE", 0.85);
        }
        return new SentimentAnalysisResponse("NEUTRAL", 0.60);
    }
}
