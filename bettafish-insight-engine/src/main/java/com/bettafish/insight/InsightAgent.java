package com.bettafish.insight;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.insight.keyword.KeywordOptimizer;
import com.bettafish.insight.tool.MediaCrawlerDbTool;
import com.bettafish.insight.tool.SentimentTool;

@Service
public class InsightAgent implements AnalysisEngine {

    private final SentimentAnalysisClient sentimentAnalysisClient;
    private final MediaCrawlerDbTool mediaCrawlerDbTool;
    private final KeywordOptimizer keywordOptimizer;

    @Autowired
    public InsightAgent(
        SentimentTool sentimentTool,
        MediaCrawlerDbTool mediaCrawlerDbTool,
        KeywordOptimizer keywordOptimizer
    ) {
        this(sentimentTool::analyze, mediaCrawlerDbTool, keywordOptimizer);
    }

    InsightAgent(SentimentAnalysisClient sentimentAnalysisClient) {
        this(sentimentAnalysisClient, new MediaCrawlerDbTool(), new KeywordOptimizer());
    }

    InsightAgent(
        SentimentAnalysisClient sentimentAnalysisClient,
        MediaCrawlerDbTool mediaCrawlerDbTool,
        KeywordOptimizer keywordOptimizer
    ) {
        this.sentimentAnalysisClient = sentimentAnalysisClient;
        this.mediaCrawlerDbTool = mediaCrawlerDbTool;
        this.keywordOptimizer = keywordOptimizer;
    }

    @Override
    public EngineResult analyze(AnalysisRequest request) {
        SentimentSignal sentiment = sentimentAnalysisClient.analyze(request.query());
        String sentimentConfidence = String.format(Locale.ROOT, "%.2f", sentiment.confidence());
        String mode = sentiment.enabled() ? "sentiment-mcp" : "sentiment-disabled";
        List<String> optimizedKeywords = keywordOptimizer.optimize(request.query());
        List<SourceReference> sources = mediaCrawlerDbTool.search(request.query(), optimizedKeywords);

        return new EngineResult(
            EngineType.INSIGHT,
            "Audience sentiment around " + request.query(),
            "InsightAgent produced a social sentiment snapshot for " + request.query()
                + " with dominant sentiment " + sentiment.label()
                + " (confidence " + sentimentConfidence + ").",
            List.of(
                "Dominant sentiment: " + sentiment.label(),
                "Sentiment confidence: " + sentimentConfidence,
                "Optimized keywords: " + String.join(", ", optimizedKeywords)
            ),
            sources,
            Map.of(
                "mode", mode,
                "sentimentLabel", sentiment.label(),
                "sentimentConfidence", sentimentConfidence
            )
        );
    }
}
