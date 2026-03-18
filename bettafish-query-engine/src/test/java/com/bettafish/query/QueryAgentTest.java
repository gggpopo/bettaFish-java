package com.bettafish.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineType;
import com.bettafish.query.tool.TavilySearchTool;

class QueryAgentTest {

    @Test
    void returnsQueryEngineResultUsingTavilyTool() {
        QueryAgent agent = new QueryAgent(new TavilySearchTool());

        var result = agent.analyze(new AnalysisRequest(
            "task-1",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals(EngineType.QUERY, result.engineType());
        assertEquals("tavily-tool", result.metadata().get("mode"));
        assertTrue(result.summary().contains("武汉大学樱花季舆情热度"));
        assertEquals(1, result.sources().size());
    }
}
