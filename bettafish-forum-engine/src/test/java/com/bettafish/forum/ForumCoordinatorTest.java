package com.bettafish.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;

class ForumCoordinatorTest {

    @Test
    void coordinatesEngineResultsIntoForumSummary() {
        ForumCoordinator coordinator = new ForumCoordinator(new ForumHost());

        var summary = coordinator.coordinate(
            new AnalysisRequest("task-1", "武汉大学樱花季舆情热度", Instant.parse("2026-03-18T00:00:00Z")),
            List.of(
                result(EngineType.QUERY, "Query headline"),
                result(EngineType.MEDIA, "Media headline"),
                result(EngineType.INSIGHT, "Insight headline")
            )
        );

        assertTrue(summary.overview().contains("3"));
        assertEquals(3, summary.consensusPoints().size());
        assertTrue(summary.openQuestions().contains("Which audience segment should be tracked more closely?"));
    }

    private static EngineResult result(EngineType engineType, String headline) {
        return new EngineResult(engineType, headline, "summary", List.of("point"), List.of(), java.util.Map.of());
    }
}
