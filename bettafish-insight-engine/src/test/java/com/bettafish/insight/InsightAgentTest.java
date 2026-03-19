package com.bettafish.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.insight.keyword.KeywordOptimizer;

class InsightAgentTest {

    @Test
    void includesSentimentSummaryInEngineResult() {
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("POSITIVE", 0.85, true),
            new com.bettafish.insight.tool.MediaCrawlerDbTool(),
            new KeywordOptimizer(ChatClient.builder(mockKeywordModel()).build())
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

    private static org.springframework.ai.openai.OpenAiChatModel mockKeywordModel() {
        return org.springframework.ai.openai.OpenAiChatModel.builder()
            .openAiApi(org.springframework.ai.openai.api.OpenAiApi.builder().apiKey("test").baseUrl("https://example.com").build())
            .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder().model("keyword-model").build())
            .build();
    }
}
