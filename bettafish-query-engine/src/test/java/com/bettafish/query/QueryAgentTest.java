package com.bettafish.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.NodeStartedEvent;
import com.bettafish.common.event.ToolCalledEvent;
import com.bettafish.common.engine.ForumGuidanceProvider;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;
import com.bettafish.query.tool.TavilySearchTool;

class QueryAgentTest {

    @Test
    void runsFullInMemoryWorkflowAndPersistsIntermediateState() {
        RecordingLlmGateway llmGateway = new RecordingLlmGateway();
        StaticForumGuidanceProvider guidanceProvider = new StaticForumGuidanceProvider();
        QueryAgent agent = new QueryAgent(new TavilySearchTool(), llmGateway, guidanceProvider, 2, 3);
        RecordingPublisher publisher = new RecordingPublisher();
        AnalysisRequest request = new AnalysisRequest(
            "task-1",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        );

        AgentState state = agent.runWorkflow(request, publisher);
        var result = agent.analyze(request, publisher);

        assertEquals("QUERY", state.getAgentName());
        assertEquals("COMPLETED", state.getStatus());
        assertEquals(2, state.getRound());
        assertEquals(3, state.getParagraphs().size());
        assertFalse(state.getFinalReport().isBlank());
        assertTrue(state.getFinalReport().contains("终稿"));
        assertEquals(List.of("结构规划1", "结构规划2", "结构规划3"),
            state.getParagraphs().stream().map(paragraph -> paragraph.getTitle()).toList());

        state.getParagraphs().forEach(paragraph -> {
            assertTrue(paragraph.isCompleted());
            assertFalse(paragraph.getCurrentDraft().isBlank());
            assertEquals(2, paragraph.getReflectionRoundsCompleted());
            assertEquals(3, paragraph.getSearchHistory().size());
            assertFalse(paragraph.getSearchHistory().getFirst().getSources().isEmpty());
        });

        assertEquals(EngineType.QUERY, result.engineType());
        assertEquals("state-machine", result.metadata().get("mode"));
        assertEquals("3", result.metadata().get("paragraphCount"));
        assertEquals("2", result.metadata().get("reflectionCount"));
        assertTrue(result.summary().contains("终稿"));
        assertTrue(result.sources().size() >= 3);
        assertTrue(llmGateway.calls().stream().allMatch(call -> call.clientName().equals("queryChatClient")));
        assertTrue(llmGateway.calls().size() >= 10);
        assertTrue(publisher.events().stream().anyMatch(NodeStartedEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(ToolCalledEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(DeltaChunkEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(AgentSpeechEvent.class::isInstance));
        assertEquals("task-1", publisher.events().getFirst().taskId());
        assertTrue(llmGateway.calls().stream()
            .map(Call::userPrompt)
            .anyMatch(prompt -> prompt.contains("请优先补齐证据缺口并追问争议来源")));
        assertEquals(1, state.getForumGuidanceHistory().size());
        assertEquals(1, state.getParagraphs().getFirst().getForumGuidanceRevisionApplied());
        assertTrue(state.getForumMessages().stream().anyMatch(message -> message.content().contains("请优先补齐证据缺口")));
    }

    @Test
    void downgradesToFallbackDefaultsWhenGatewayCannotReturnJson() {
        FallbackOnlyLlmGateway llmGateway = new FallbackOnlyLlmGateway();
        QueryAgent agent = new QueryAgent(new TavilySearchTool(), llmGateway, 2, 3);

        AgentState state = agent.runWorkflow(new AnalysisRequest(
            "task-2",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals("COMPLETED", state.getStatus());
        assertEquals(3, state.getParagraphs().size());
        assertTrue(state.getParagraphs().stream().allMatch(paragraph -> paragraph.isCompleted()));
        assertFalse(state.getFinalReport().isBlank());
        assertTrue(llmGateway.fallbackCount() > 0);
    }

    private static final class RecordingLlmGateway implements LlmGateway {

        private final List<Call> calls = new ArrayList<>();
        private int summaryCalls;
        private final Map<String, Integer> reflectionDecisionCallsByParagraph = new HashMap<>();

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType) {
            calls.add(new Call(clientName, systemPrompt, userPrompt, responseType.getSimpleName()));

            if (responseType.equals(QueryAgent.ParagraphSummaryResponse.class)) {
                summaryCalls++;
                return responseType.cast(new QueryAgent.ParagraphSummaryResponse("段落总结-" + summaryCalls));
            }
            if (responseType.equals(QueryAgent.ReflectionSummaryResponse.class)) {
                summaryCalls++;
                return responseType.cast(new QueryAgent.ReflectionSummaryResponse("反思扩写-" + summaryCalls));
            }
            if (responseType.equals(QueryAgent.ReflectionDecisionResponse.class)) {
                String paragraphKey = extractParagraphKey(userPrompt);
                int count = reflectionDecisionCallsByParagraph.getOrDefault(paragraphKey, 0);
                reflectionDecisionCallsByParagraph.put(paragraphKey, count + 1);
                if (count >= 2) {
                    return responseType.cast(new QueryAgent.ReflectionDecisionResponse(false, "", "enough"));
                }
                return responseType.cast(new QueryAgent.ReflectionDecisionResponse(
                    true,
                    "补充检索-" + paragraphKey + "-" + (count + 1),
                    "gap-" + paragraphKey + "-" + (count + 1)
                ));
            }
            if (responseType.equals(QueryAgent.FinalReportResponse.class)) {
                return responseType.cast(new QueryAgent.FinalReportResponse("终稿：武汉大学樱花季舆情热度"));
            }

            throw new IllegalArgumentException("Unexpected class response type: " + responseType);
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            return callJson(clientName, systemPrompt, userPrompt, responseType);
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType) {
            calls.add(new Call(clientName, systemPrompt, userPrompt, responseType.getType().getTypeName()));

            @SuppressWarnings("unchecked")
            T result = (T) List.of(
                new QueryAgent.ParagraphPlan("结构规划1", "第一段内容范围"),
                new QueryAgent.ParagraphPlan("结构规划2", "第二段内容范围"),
                new QueryAgent.ParagraphPlan("结构规划3", "第三段内容范围")
            );
            return result;
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            return callJson(clientName, systemPrompt, userPrompt, responseType);
        }

        List<Call> calls() {
            return calls;
        }

        private String extractParagraphKey(String userPrompt) {
            int titleIndex = userPrompt.indexOf("段落标题：");
            if (titleIndex < 0) {
                return "unknown";
            }
            String titlePart = userPrompt.substring(titleIndex + "段落标题：".length());
            int lineEnd = titlePart.indexOf('\n');
            return (lineEnd >= 0 ? titlePart.substring(0, lineEnd) : titlePart).trim();
        }
    }

    private record Call(String clientName, String systemPrompt, String userPrompt, String responseType) {
    }

    private static final class FallbackOnlyLlmGateway implements LlmGateway {

        private int fallbackCount;

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType) {
            throw new UnsupportedOperationException("Fallback overload expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            fallbackCount++;
            return fallbackSupplier.get();
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType) {
            throw new UnsupportedOperationException("Fallback overload expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            fallbackCount++;
            return fallbackSupplier.get();
        }

        int fallbackCount() {
            return fallbackCount;
        }
    }

    private static final class RecordingPublisher implements AnalysisEventPublisher {

        private final List<AnalysisEvent> events = new ArrayList<>();

        @Override
        public void publish(AnalysisEvent event) {
            events.add(event);
        }

        List<AnalysisEvent> events() {
            return events;
        }
    }

    private static final class StaticForumGuidanceProvider implements ForumGuidanceProvider {

        private final ForumGuidance guidance = new ForumGuidance(
            1,
            "主持人指导",
            List.of("关注传播链路"),
            List.of("争议源头是什么"),
            List.of("证据缺口：源头样本"),
            "请优先补齐证据缺口并追问争议来源"
        );

        @Override
        public List<ForumMessage> transcript(String taskId) {
            return List.of(new ForumMessage("ForumHost", "host", guidance.promptAddendum(), Instant.parse("2026-03-18T00:00:00Z")));
        }

        @Override
        public List<ForumGuidance> guidanceHistory(String taskId) {
            return List.of(guidance);
        }
    }
}
