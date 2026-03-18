package com.bettafish.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportSection;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;

class AnalysisCoordinatorTest {

    @Test
    void startsAnalysisAndStoresCompletedSnapshot() {
        Executor sameThreadExecutor = Runnable::run;
        InMemoryAnalysisTaskRepository taskRepository = new InMemoryAnalysisTaskRepository();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY, "Query summary"),
                engine(EngineType.MEDIA, "Media summary"),
                engine(EngineType.INSIGHT, "Insight summary")
            ),
            forumCoordinator(),
            reportGenerator(),
            taskRepository,
            sameThreadExecutor
        );

        var snapshot = coordinator.startAnalysis("分析武汉大学樱花季舆情热度");

        assertNotNull(snapshot.taskId());
        assertEquals(AnalysisStatus.COMPLETED, snapshot.status());
        assertEquals(3, snapshot.engineResults().size());
        assertEquals("Forum overview", snapshot.forumSummary().overview());
        assertEquals(4, snapshot.report().sections().size());
        assertTrue(taskRepository.findById(snapshot.taskId()).isPresent());
    }

    private static AnalysisEngine engine(EngineType engineType, String summary) {
        return request -> new EngineResult(
            engineType,
            engineType.name() + " headline",
            summary,
            List.of("key point"),
            List.of(),
            java.util.Map.of()
        );
    }

    private static ForumCoordinator forumCoordinator() {
        return (request, results) -> new ForumSummary(
            "Forum overview",
            List.of("Consensus"),
            List.of("Open question")
        );
    }

    private static ReportGenerator reportGenerator() {
        return (request, input) -> new ReportDocument(
            "Report title",
            "Report summary",
            List.of(
                new ReportSection("Query", "Query section"),
                new ReportSection("Media", "Media section"),
                new ReportSection("Insight", "Insight section"),
                new ReportSection("Forum", "Forum section")
            ),
            "<html><body>stub</body></html>"
        );
    }
}
