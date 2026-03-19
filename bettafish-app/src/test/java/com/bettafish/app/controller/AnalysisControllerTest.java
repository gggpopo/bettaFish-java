package com.bettafish.app.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.bettafish.app.event.InMemoryEventBus;
import com.bettafish.app.service.AnalysisCoordinator;
import com.bettafish.app.service.InMemoryAnalysisTaskRepository;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;

class AnalysisControllerTest {

    @Test
    void createsAndReadsAnalysisTasksOverHttp() throws Exception {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY),
                engine(EngineType.MEDIA),
                engine(EngineType.INSIGHT)
            ),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            sameThreadExecutor(),
            eventBus
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

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
            .andExpect(jsonPath("$.report.title").value("BettaFish analysis report"))
            .andExpect(jsonPath("$.report.documentIr.meta.title").value("BettaFish analysis report"))
            .andExpect(jsonPath("$.report.documentIr.blocks.length()").value(2));
    }

    @Test
    void streamsTaskEventsOverSse() throws Exception {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY),
                engine(EngineType.MEDIA),
                engine(EngineType.INSIGHT)
            ),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            sameThreadExecutor(),
            eventBus
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

        String responseBody = mockMvc.perform(post("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"分析武汉大学樱花季舆情热度"}
                    """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String taskId = JsonTestSupport.readJsonPath(responseBody, "$.taskId");

        MvcResult asyncResult = mockMvc.perform(get("/api/analysis/{taskId}/events", taskId))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:EngineStartedEvent")))
            .andExpect(content().string(containsString("event:AnalysisCompleteEvent")))
            .andExpect(content().string(containsString("\"taskId\":\"" + taskId + "\"")))
            .andExpect(content().string(containsString("\"engineName\":\"QUERY\"")));
    }

    @Test
    void returnsNotFoundWhenStreamingUnknownTask() throws Exception {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(engine(EngineType.QUERY)),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            sameThreadExecutor(),
            eventBus
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

        mockMvc.perform(get("/api/analysis/{taskId}/events", "missing-task"))
            .andExpect(status().isNotFound());
    }

    private static Executor sameThreadExecutor() {
        return Runnable::run;
    }

    private static AnalysisEngine engine(EngineType engineType) {
        return new AnalysisEngine() {
            @Override
            public String engineName() {
                return engineType.name();
            }

            @Override
            public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
                return new EngineResult(
                    engineType,
                    engineType.name() + " headline",
                    engineType.name() + " summary",
                    List.of(engineType.name() + " point"),
                    List.of(),
                    java.util.Map.of()
                );
            }
        };
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
            new DocumentIr(
                new DocumentMeta(
                    "BettaFish analysis report",
                    "Report summary",
                    request.query(),
                    "default",
                    Instant.parse("2026-03-18T00:00:00Z")
                ),
                List.of(
                    new DocumentBlock.HeadingBlock(2, "Forum"),
                    new DocumentBlock.ParagraphBlock("Forum section")
                )
            ),
            "<html><body>stub</body></html>"
        );
    }
}
