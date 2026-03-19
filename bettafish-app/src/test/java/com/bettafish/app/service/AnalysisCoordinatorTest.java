package com.bettafish.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import com.bettafish.app.event.InMemoryEventBus;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.AnalysisCompleteEvent;
import com.bettafish.common.event.AnalysisFailedEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.EngineStartedEvent;
import com.bettafish.common.event.HostCommentEvent;
import com.bettafish.common.event.NodeStartedEvent;
import com.bettafish.common.event.ToolCalledEvent;

class AnalysisCoordinatorTest {

    @Test
    void startsAnalysisAndStoresCompletedSnapshot() {
        Executor sameThreadExecutor = Runnable::run;
        InMemoryAnalysisTaskRepository taskRepository = new InMemoryAnalysisTaskRepository();
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY, "Query summary"),
                engine(EngineType.MEDIA, "Media summary"),
                engine(EngineType.INSIGHT, "Insight summary")
            ),
            forumCoordinator(),
            reportGenerator(),
            taskRepository,
            sameThreadExecutor,
            eventBus
        );

        var snapshot = coordinator.startAnalysis("分析武汉大学樱花季舆情热度");
        List<?> events = eventBus.history(snapshot.taskId());

        assertNotNull(snapshot.taskId());
        assertEquals(AnalysisStatus.COMPLETED, snapshot.status());
        assertEquals(3, snapshot.engineResults().size());
        assertEquals("Forum overview", snapshot.forumSummary().overview());
        assertEquals(4, snapshot.report().sections().size());
        assertEquals(8, snapshot.report().documentIr().blocks().size());
        assertTrue(taskRepository.findById(snapshot.taskId()).isPresent());
        assertEquals(List.of("QUERY", "MEDIA", "INSIGHT"),
            events.stream()
                .filter(EngineStartedEvent.class::isInstance)
                .map(EngineStartedEvent.class::cast)
                .map(EngineStartedEvent::engineName)
                .toList());
        assertTrue(events.stream().anyMatch(NodeStartedEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(ToolCalledEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(AgentSpeechEvent.class::isInstance));
        assertTrue(events.stream()
            .filter(AgentSpeechEvent.class::isInstance)
            .map(AgentSpeechEvent.class::cast)
            .anyMatch(event -> event.content().contains("QUERY headline")));
        assertTrue(events.stream().anyMatch(HostCommentEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(DeltaChunkEvent.class::isInstance));
        assertTrue(events.getLast() instanceof AnalysisCompleteEvent);
    }

    @Test
    void publishesFailureEventWhenAnalysisCrashes() {
        Executor sameThreadExecutor = Runnable::run;
        InMemoryAnalysisTaskRepository taskRepository = new InMemoryAnalysisTaskRepository();
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(request -> {
                throw new IllegalStateException("boom");
            }),
            forumCoordinator(),
            reportGenerator(),
            taskRepository,
            sameThreadExecutor,
            eventBus
        );

        var snapshot = coordinator.startAnalysis("分析失败任务");

        assertEquals(AnalysisStatus.FAILED, snapshot.status());
        assertTrue(eventBus.history(snapshot.taskId()).stream().anyMatch(AnalysisFailedEvent.class::isInstance));
    }

    private static AnalysisEngine engine(EngineType engineType, String summary) {
        return new AnalysisEngine() {
            @Override
            public String engineName() {
                return engineType.name();
            }

            @Override
            public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
                return analyze(request, event -> {
                });
            }

            @Override
            public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request,
                                        com.bettafish.common.event.AnalysisEventPublisher publisher) {
                Instant now = Instant.parse("2026-03-18T00:00:00Z");
                publisher.publish(new NodeStartedEvent(request.taskId(), engineType.name(), "PLAN", now));
                publisher.publish(new ToolCalledEvent(request.taskId(), engineType.name(), "stub-tool", request.query(), "unit-test", now));
                publisher.publish(new DeltaChunkEvent(request.taskId(), engineType.name(), "summary", summary, 1, now));
                return new EngineResult(
                    engineType,
                    engineType.name() + " headline",
                    summary,
                    List.of("key point"),
                    List.of(),
                    java.util.Map.of()
                );
            }
        };
    }

    private static ForumCoordinator forumCoordinator() {
        return new ForumCoordinator() {
            @Override
            public ForumSummary coordinate(com.bettafish.common.api.AnalysisRequest request, List<EngineResult> results) {
                return coordinate(request, results, event -> {
                });
            }

            @Override
            public ForumSummary coordinate(com.bettafish.common.api.AnalysisRequest request,
                                           List<EngineResult> results,
                                           com.bettafish.common.event.AnalysisEventPublisher publisher) {
                Instant now = Instant.parse("2026-03-18T00:00:01Z");
                publisher.publish(new AgentSpeechEvent(request.taskId(), "QUERY", "观点：Query summary", now));
                publisher.publish(new HostCommentEvent(request.taskId(), "ForumHost", "主持总结", now));
                return new ForumSummary(
                    "Forum overview",
                    List.of("Consensus"),
                    List.of("Open question")
                );
            }
        };
    }

    private static ReportGenerator reportGenerator() {
        return new ReportGenerator() {
            @Override
            public ReportDocument generate(com.bettafish.common.api.AnalysisRequest request, com.bettafish.common.api.ReportInput input) {
                return generate(request, input, event -> {
                });
            }

            @Override
            public ReportDocument generate(com.bettafish.common.api.AnalysisRequest request,
                                           com.bettafish.common.api.ReportInput input,
                                           com.bettafish.common.event.AnalysisEventPublisher publisher) {
                publisher.publish(new DeltaChunkEvent(
                    request.taskId(),
                    "REPORT",
                    "report",
                    "Report draft chunk",
                    1,
                    Instant.parse("2026-03-18T00:00:02Z")
                ));
                return new ReportDocument(
                    "Report title",
                    "Report summary",
                    new DocumentIr(
                        new DocumentMeta(
                            "Report title",
                            "Report summary",
                            request.query(),
                            "default",
                            Instant.parse("2026-03-18T00:00:02Z")
                        ),
                        List.of(
                            new DocumentBlock.HeadingBlock(2, "Query"),
                            new DocumentBlock.ParagraphBlock("Query section"),
                            new DocumentBlock.HeadingBlock(2, "Media"),
                            new DocumentBlock.ParagraphBlock("Media section"),
                            new DocumentBlock.HeadingBlock(2, "Insight"),
                            new DocumentBlock.ParagraphBlock("Insight section"),
                            new DocumentBlock.HeadingBlock(2, "Forum"),
                            new DocumentBlock.ParagraphBlock("Forum section")
                        )
                    ),
                    "<html><body>stub</body></html>"
                );
            }
        };
    }
}
