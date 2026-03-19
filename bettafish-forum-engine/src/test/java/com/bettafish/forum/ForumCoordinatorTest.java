package com.bettafish.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.HostCommentEvent;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.common.model.ForumGuidance;
import com.fasterxml.jackson.core.type.TypeReference;

class ForumCoordinatorTest {

    @Test
    void accumulatesTranscriptAndGeneratesStructuredGuidance() {
        RecordingLlmGateway llmGateway = new RecordingLlmGateway();
        RecordingPublisher publisher = new RecordingPublisher();
        ForumHost forumHost = new ForumHost(llmGateway);
        ForumTranscriptService transcriptService = new ForumTranscriptService(forumHost, publisher, 2);
        ForumCoordinator coordinator = new ForumCoordinator(forumHost, transcriptService);
        AnalysisRequest request = new AnalysisRequest("task-1", "武汉大学樱花季舆情热度", Instant.parse("2026-03-18T00:00:00Z"));

        transcriptService.onEvent(new AgentSpeechEvent(request.taskId(), "QUERY", "Query headline | Query summary", Instant.parse("2026-03-18T00:00:01Z")));
        transcriptService.onEvent(new AgentSpeechEvent(request.taskId(), "MEDIA", "Media headline | Media summary", Instant.parse("2026-03-18T00:00:02Z")));

        var summary = coordinator.coordinate(
            request,
            List.of(
                result(EngineType.QUERY, "Query headline"),
                result(EngineType.MEDIA, "Media headline"),
                result(EngineType.INSIGHT, "Insight headline")
            ),
            publisher
        );

        assertEquals(2, summary.transcript().size());
        assertEquals(1, summary.guidanceHistory().size());
        assertEquals("主持人指导-1", summary.overview());
        assertEquals(List.of("关注角度-1", "关注角度-2"), summary.consensusPoints());
        assertTrue(summary.openQuestions().contains("追问-1"));
        assertTrue(summary.openQuestions().contains("证据缺口-1"));
        assertEquals(1, publisher.events().stream().filter(HostCommentEvent.class::isInstance).count());
        assertTrue(llmGateway.calls().getFirst().userPrompt().contains("Query headline | Query summary"));
        assertTrue(llmGateway.calls().getFirst().userPrompt().contains("Media headline | Media summary"));
    }

    private static EngineResult result(EngineType engineType, String headline) {
        return new EngineResult(engineType, headline, "summary", List.of("point"), List.of(), java.util.Map.of());
    }

    private static final class RecordingLlmGateway implements LlmGateway {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            calls.add(new Call(clientName, systemPrompt, userPrompt));
            if (responseType.equals(ForumGuidance.class)) {
                return responseType.cast(new ForumGuidance(
                    1,
                    "主持人指导-1",
                    List.of("关注角度-1", "关注角度-2"),
                    List.of("追问-1"),
                    List.of("证据缺口-1"),
                    "请优先补齐证据缺口-1"
                ));
            }
            throw new IllegalArgumentException("Unexpected response type: " + responseType);
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("Class response type expected");
        }

        List<Call> calls() {
            return calls;
        }
    }

    private record Call(String clientName, String systemPrompt, String userPrompt) {
    }

    private static final class RecordingPublisher implements AnalysisEventPublisher {

        private final List<AnalysisEvent> events = new ArrayList<>();

        @Override
        public void publish(AnalysisEvent event) {
            events.add(event);
        }

        private List<AnalysisEvent> events() {
            return events;
        }
    }
}
