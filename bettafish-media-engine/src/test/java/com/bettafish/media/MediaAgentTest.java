package com.bettafish.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineType;
import com.bettafish.media.tool.BochaSearchTool;

class MediaAgentTest {

    @Test
    void returnsMediaEngineResultUsingBochaTool() {
        MediaAgent agent = new MediaAgent(new BochaSearchTool());

        var result = agent.analyze(new AnalysisRequest(
            "task-1",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals(EngineType.MEDIA, result.engineType());
        assertEquals("bocha-tool", result.metadata().get("mode"));
        assertTrue(result.summary().contains("武汉大学樱花季舆情热度"));
        assertEquals(1, result.sources().size());
    }
}
