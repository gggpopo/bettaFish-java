package com.bettafish.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.ReportInput;
import com.bettafish.report.renderer.HtmlRenderer;

class ReportAgentTest {

    @Test
    void generatesHtmlReportFromEngineResults() {
        ReportAgent reportAgent = new ReportAgent(new HtmlRenderer());

        var report = reportAgent.generate(
            new AnalysisRequest("task-1", "武汉大学樱花季舆情热度", Instant.parse("2026-03-18T00:00:00Z")),
            new ReportInput(
                "武汉大学樱花季舆情热度",
                List.of(
                    result(EngineType.QUERY),
                    result(EngineType.MEDIA),
                    result(EngineType.INSIGHT)
                ),
                new ForumSummary("Forum overview", List.of("Consensus"), List.of("Open question"))
            )
        );

        assertEquals("BettaFish analysis report", report.title());
        assertEquals(4, report.sections().size());
        assertTrue(report.html().contains("<section>"));
    }

    private static EngineResult result(EngineType engineType) {
        return new EngineResult(
            engineType,
            engineType.name() + " headline",
            engineType.name() + " summary",
            List.of(engineType.name() + " point"),
            List.of(),
            java.util.Map.of()
        );
    }
}
