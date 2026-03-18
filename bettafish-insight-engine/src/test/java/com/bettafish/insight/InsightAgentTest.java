package com.bettafish.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;

class InsightAgentTest {

    @Test
    void includesSentimentSummaryInEngineResult() {
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("POSITIVE", 0.85, true)
        );

        var result = agent.analyze(new AnalysisRequest(
            "task-1",
            "武汉大学樱花太棒了",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals("POSITIVE", result.metadata().get("sentimentLabel"));
        assertEquals("0.85", result.metadata().get("sentimentConfidence"));
        assertEquals("sentiment-mcp", result.metadata().get("mode"));
        assertTrue(result.summary().contains("POSITIVE"));
        assertTrue(result.keyPoints().contains("Dominant sentiment: POSITIVE"));
    }
}
