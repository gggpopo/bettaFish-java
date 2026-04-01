package com.bettafish.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.SourceReference;
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
import com.bettafish.media.tool.BochaSearchTool;

class MediaAgentTest {

    @Test
    void returnsMediaEngineResultUsingBochaTool() {
        MediaAgent agent = new MediaAgent(new BochaSearchTool(), new FallbackTextGateway(), ForumGuidanceProvider.noop());

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

    @Test
    void injectsForumGuidanceIntoSummaryAndReflectionPrompts() {
        RecordingLlmGateway gateway = new RecordingLlmGateway();
        StaticForumGuidanceProvider guidanceProvider = new StaticForumGuidanceProvider();
        MediaAgent agent = new MediaAgent(
            new StaticBochaSearchTool(),
            gateway,
            guidanceProvider
        );

        var result = agent.analyze(new AnalysisRequest(
            "task-2",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals(EngineType.MEDIA, result.engineType());
        assertEquals("2", result.metadata().get("forumGuidanceRevision"));
        assertEquals("请优先追踪二创扩散路径并补足短视频证据", result.metadata().get("forumGuidancePrompt"));
        assertTrue(gateway.prompts().stream().allMatch(prompt -> prompt.contains("请优先追踪二创扩散路径并补足短视频证据")));
        assertTrue(gateway.prompts().stream().anyMatch(prompt -> prompt.contains("论坛主持指导")));
        assertTrue(result.summary().contains("反思补充"));
    }

    @Test
    void runsGuidanceDrivenWorkflowAndRecordsRefinementSearch() {
        RecordingLlmGateway gateway = new RecordingLlmGateway();
        StaticForumGuidanceProvider guidanceProvider = new StaticForumGuidanceProvider();
        MediaAgent agent = new MediaAgent(
            new StaticBochaSearchTool(),
            gateway,
            guidanceProvider
        );
        RecordingPublisher publisher = new RecordingPublisher();

        AgentState state = agent.runWorkflow(new AnalysisRequest(
            "task-3",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        ), publisher);

        assertEquals("MEDIA", state.getAgentName());
        assertEquals("COMPLETED", state.getStatus());
        assertEquals("FinalizeReportNode", state.getCurrentNode());
        assertEquals(1, state.getRound());
        assertEquals(1, state.getParagraphs().size());
        assertTrue(state.getFinalReport().contains("反思补充"));
        assertEquals(2, state.getParagraphs().getFirst().getSearchHistory().size());
        assertEquals(1, state.getParagraphs().getFirst().getReflectionRoundsCompleted());
        assertEquals(2, state.getParagraphs().getFirst().getForumGuidanceRevisionApplied());
        assertTrue(state.getParagraphs().getFirst().getForumGuidancePrompt().contains("二创扩散路径"));
        assertTrue(state.getParagraphs().getFirst().getSearchHistory().get(1).getSearchQuery().contains("二创"));
        assertTrue(state.getParagraphs().getFirst().getSearchHistory().get(1).getSearchQuery().contains("短视频"));
        assertEquals(2, publisher.events().stream().filter(ToolCalledEvent.class::isInstance).count());
        assertTrue(publisher.events().stream().anyMatch(NodeStartedEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(DeltaChunkEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(AgentSpeechEvent.class::isInstance));
    }

    @Test
    void stopsWorkflowWhenStructuredDecisionRejectsRefinement() {
        NoRefineDecisionGateway gateway = new NoRefineDecisionGateway();
        MediaAgent agent = new MediaAgent(
            new StaticBochaSearchTool(),
            gateway,
            new StaticForumGuidanceProvider()
        );

        AgentState state = agent.runWorkflow(new AnalysisRequest(
            "task-4",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals(1, state.getParagraphs().getFirst().getSearchHistory().size());
        assertEquals(0, state.getParagraphs().getFirst().getReflectionRoundsCompleted());
        assertTrue(gateway.decisionPromptSeen());
    }

    private static final class StaticBochaSearchTool extends BochaSearchTool {

        @Override
        public List<SourceReference> search(String query) {
            return List.of(
                new SourceReference(
                    "短视频平台热度上升",
                    "https://media.example.test/video-1",
                    "短视频平台二创内容带动热度持续攀升。"
                ),
                new SourceReference(
                    "图文平台聚焦预约与限流",
                    "https://media.example.test/post-2",
                    "图文平台舆论集中在预约机制和校内限流安排。"
                )
            );
        }
    }

    private static final class RecordingLlmGateway implements LlmGateway {

        private final List<String> prompts = new ArrayList<>();
        private int callCount;

        @Override
        public String callText(String clientName, String systemPrompt, String userPrompt,
                               java.util.function.Supplier<String> fallbackSupplier) {
            prompts.add(userPrompt);
            callCount++;
            if (callCount == 1) {
                return "首轮总结：视觉传播与图文扩散并存。";
            }
            return "反思补充：请优先追踪二创扩散路径并补足短视频证据。";
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            if (responseType.getSimpleName().equals("ReflectionDecisionResponse")) {
                return responseType.cast(new MediaAgent.ReflectionDecisionResponse(
                    true,
                    "武汉大学樱花季舆情热度 二创 短视频",
                    "need-more-video-evidence"
                ));
            }
            throw new UnsupportedOperationException("Unexpected class response type");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              com.fasterxml.jackson.core.type.TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("Text response expected");
        }

        List<String> prompts() {
            return prompts;
        }
    }

    private static final class FallbackTextGateway implements LlmGateway {

        @Override
        public String callText(String clientName, String systemPrompt, String userPrompt,
                               java.util.function.Supplier<String> fallbackSupplier) {
            return fallbackSupplier.get();
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("Text response expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              com.fasterxml.jackson.core.type.TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("Text response expected");
        }
    }

    private static final class NoRefineDecisionGateway implements LlmGateway {

        private boolean decisionPromptSeen;
        private final List<String> prompts = new ArrayList<>();
        private int callCount;

        @Override
        public String callText(String clientName, String systemPrompt, String userPrompt,
                               java.util.function.Supplier<String> fallbackSupplier) {
            prompts.add(userPrompt);
            callCount++;
            if (callCount == 1) {
                return "首轮总结：视觉传播与图文扩散并存。";
            }
            return "反思补充：请优先追踪二创扩散路径并补足短视频证据。";
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            decisionPromptSeen = true;
            return fallbackSupplier.get();
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              com.fasterxml.jackson.core.type.TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("Text/class response expected");
        }

        boolean decisionPromptSeen() {
            return decisionPromptSeen;
        }
    }

    private static final class StaticForumGuidanceProvider implements ForumGuidanceProvider {

        private final ForumGuidance guidance = new ForumGuidance(
            2,
            "主持人要求补齐短视频扩散证据",
            List.of("关注短视频二创扩散"),
            List.of("二创内容如何带动平台热度"),
            List.of("短视频平台样本不足"),
            "请优先追踪二创扩散路径并补足短视频证据"
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
}
