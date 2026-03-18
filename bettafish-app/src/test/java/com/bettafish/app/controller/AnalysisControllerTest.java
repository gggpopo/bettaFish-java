package com.bettafish.app.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.bettafish.app.service.AnalysisCoordinator;
import com.bettafish.app.service.InMemoryAnalysisTaskRepository;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportSection;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;

class AnalysisControllerTest {

    @Test
    void createsAndReadsAnalysisTasksOverHttp() throws Exception {
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY),
                engine(EngineType.MEDIA),
                engine(EngineType.INSIGHT)
            ),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            sameThreadExecutor()
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator)).build();

        String responseBody = mockMvc.perform(post("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"分析武汉大学樱花季舆情热度"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.engineResults.length()").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String taskId = JsonTestSupport.readJsonPath(responseBody, "$.taskId");

        mockMvc.perform(get("/api/analysis/{taskId}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId))
            .andExpect(jsonPath("$.report.title").value("BettaFish analysis report"));
    }

    private static Executor sameThreadExecutor() {
        return Runnable::run;
    }

    private static AnalysisEngine engine(EngineType engineType) {
        return request -> new EngineResult(
            engineType,
            engineType.name() + " headline",
            engineType.name() + " summary",
            List.of(engineType.name() + " point"),
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
            "BettaFish analysis report",
            "Report summary",
            List.of(new ReportSection("Forum", "Forum section")),
            "<html><body>stub</body></html>"
        );
    }
}
