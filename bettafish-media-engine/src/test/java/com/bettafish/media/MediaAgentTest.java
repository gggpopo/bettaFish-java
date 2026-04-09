package com.bettafish.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        assertEquals("narrative-v1", result.metadata().get("structuredOutputFormat"));
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
        assertTrue(gateway.summaryJsonSeen());
        assertTrue(gateway.reflectionSummaryJsonSeen());
        assertTrue(gateway.finalConclusionJsonSeen());
        assertTrue(result.summary().contains("最终结论"));
        assertEquals("最终摘要：多模态传播结构已经稳定，短视频二创与图文讨论形成接力扩散。", result.metadata().get("draftSummary"));
        assertEquals("最终结论：视觉传播以短视频二创扩散为主，图文平台持续放大预约与限流讨论。", result.metadata().get("finalConclusion"));
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
        assertTrue(state.getFinalReport().contains("最终结论"));
        assertEquals(2, state.getParagraphs().getFirst().getSearchHistory().size());
        assertEquals(1, state.getParagraphs().getFirst().getReflectionRoundsCompleted());
        assertEquals(2, state.getParagraphs().getFirst().getForumGuidanceRevisionApplied());
        assertTrue(state.getParagraphs().getFirst().getForumGuidancePrompt().contains("二创扩散路径"));
        assertTrue(state.getParagraphs().getFirst().getSearchHistory().get(1).getSearchQuery().contains("二创"));
        assertTrue(state.getParagraphs().getFirst().getSearchHistory().get(1).getSearchQuery().contains("短视频"));
        assertEquals(2, publisher.events().stream().filter(ToolCalledEvent.class::isInstance).count());
        assertTrue(state.getFinalReport().contains("最终结论"));
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
        private boolean summaryJsonSeen;
        private boolean reflectionSummaryJsonSeen;
        private boolean finalConclusionJsonSeen;

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
            prompts.add(userPrompt);
            if (systemPrompt.contains("首轮总结器")) {
                summaryJsonSeen = true;
            }
            if (systemPrompt.contains("反思总结器")) {
                reflectionSummaryJsonSeen = true;
            }
            if (systemPrompt.contains("最终结论器")) {
                finalConclusionJsonSeen = true;
            }
            if (responseType.getSimpleName().equals("ReflectionDecisionResponse")) {
                return responseType.cast(new MediaAgent.ReflectionDecisionResponse(
                    true,
                    "武汉大学樱花季舆情热度 二创 短视频",
                    "need-more-video-evidence"
                ));
            }
            if (responseType.getSimpleName().equals("StructuredNarrativeOutput")) {
                if (summaryJsonSeen && !reflectionSummaryJsonSeen) {
                    return instantiateStructuredNarrative(
                        responseType,
                        "首轮总结：视觉传播与图文扩散并存，短视频和图文平台呈现双峰扩散。",
                        List.of("短视频平台热度攀升", "图文平台聚焦预约与限流"),
                        List.of("短视频样本仍需补齐"),
                        "阶段结论：短视频与图文扩散并行。"
                    );
                }
                if (reflectionSummaryJsonSeen && !finalConclusionJsonSeen) {
                    return instantiateStructuredNarrative(
                        responseType,
                        "反思总结：短视频二创扩散带动图文平台跟进，预约与限流讨论成为第二波传播焦点。",
                        List.of("短视频二创是主要扩散器", "图文平台放大预约与限流争议"),
                        List.of("校内直播样本仍然不足"),
                        "阶段结论：二创扩散已成为传播主轴。"
                    );
                }
                return instantiateStructuredNarrative(
                    responseType,
                    "最终摘要：多模态传播结构已经稳定，短视频二创与图文讨论形成接力扩散。",
                    List.of("传播峰值由二创内容触发", "图文讨论承接预约与限流争议"),
                    List.of("直播平台样本仍偏少"),
                    "最终结论：视觉传播以短视频二创扩散为主，图文平台持续放大预约与限流讨论。"
                );
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

        boolean summaryJsonSeen() {
            return summaryJsonSeen;
        }

        boolean reflectionSummaryJsonSeen() {
            return reflectionSummaryJsonSeen;
        }

        boolean finalConclusionJsonSeen() {
            return finalConclusionJsonSeen;
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
            return fallbackSupplier.get();
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
            prompts.add(userPrompt);
            if (responseType.getSimpleName().equals("ReflectionDecisionResponse")) {
                decisionPromptSeen = true;
                return fallbackSupplier.get();
            }
            if (responseType.getSimpleName().equals("StructuredNarrativeOutput")) {
                return instantiateStructuredNarrative(
                    responseType,
                    "首轮总结：视觉传播与图文扩散并存。",
                    List.of("短视频平台热度提升", "图文平台讨论预约与限流"),
                    List.of("短视频证据还需补齐"),
                    "最终结论：传播以短视频与图文接力扩散为主。"
                );
            }
            throw new UnsupportedOperationException("Unexpected class response type");
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

    private static <T> T instantiateStructuredNarrative(Class<T> responseType,
                                                        String summary,
                                                        List<String> keyPoints,
                                                        List<String> evidenceGaps,
                                                        String finalConclusion) {
        try {
            Constructor<T> constructor = responseType.getDeclaredConstructor(String.class, List.class, List.class, String.class);
            return constructor.newInstance(summary, keyPoints, evidenceGaps, finalConclusion);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to construct structured narrative output", ex);
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
