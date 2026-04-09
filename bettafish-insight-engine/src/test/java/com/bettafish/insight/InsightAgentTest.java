package com.bettafish.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bettafish.common.api.AnalysisRequest;
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
import com.bettafish.insight.keyword.KeywordOptimizer;

class InsightAgentTest {

    @Test
    void includesSentimentSummaryInEngineResult() {
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("POSITIVE", 0.85, true),
            new com.bettafish.insight.tool.MediaCrawlerDbTool(null, new com.bettafish.common.config.DatabaseSearchProperties()),
            new KeywordOptimizer(new DeterministicKeywordGateway())
        );

        var result = agent.analyze(new AnalysisRequest(
            "task-1",
            "武汉大学樱花太棒了",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals("POSITIVE", result.metadata().get("sentimentLabel"));
        assertEquals("0.85", result.metadata().get("sentimentConfidence"));
        assertEquals("sentiment-mcp", result.metadata().get("mode"));
        assertTrue(result.summary().contains("POSITIVE"));
        assertTrue(result.keyPoints().contains("Dominant sentiment: POSITIVE"));
        assertEquals("narrative-v1", result.metadata().get("structuredOutputFormat"));
    }

    @Test
    void injectsForumGuidanceIntoInsightSummaryAndReflectionPrompts() {
        RecordingInsightGateway gateway = new RecordingInsightGateway();
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("NEUTRAL", 0.62, true),
            new com.bettafish.insight.tool.MediaCrawlerDbTool(null, new com.bettafish.common.config.DatabaseSearchProperties()),
            new KeywordOptimizer(gateway),
            gateway,
            new StaticForumGuidanceProvider()
        );

        var result = agent.analyze(new AnalysisRequest(
            "task-2",
            "武汉大学樱花预约争议",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals("3", result.metadata().get("forumGuidanceRevision"));
        assertEquals("请优先解释争议群体差异并补足预约吐槽样本", result.metadata().get("forumGuidancePrompt"));
        assertTrue(gateway.textPrompts().stream().allMatch(prompt -> prompt.contains("请优先解释争议群体差异并补足预约吐槽样本")));
        assertTrue(gateway.textPrompts().stream().anyMatch(prompt -> prompt.contains("论坛主持指导")));
        assertTrue(gateway.summaryJsonSeen());
        assertTrue(gateway.reflectionSummaryJsonSeen());
        assertTrue(gateway.finalConclusionJsonSeen());
        assertTrue(result.summary().contains("最终结论"));
        assertEquals("最终摘要：争议焦点集中在不同群体对预约公平性和体验成本的不同感知。", result.metadata().get("draftSummary"));
        assertEquals("最终结论：预约争议的核心矛盾集中在游客与学生群体预期不一致，吐槽样本主要放大排队与抢票焦虑。", result.metadata().get("finalConclusion"));
    }

    @Test
    void runsGuidanceDrivenWorkflowAndRecordsRefinementSearch() {
        RecordingInsightGateway gateway = new RecordingInsightGateway();
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("NEUTRAL", 0.62, true),
            new com.bettafish.insight.tool.MediaCrawlerDbTool(null, new com.bettafish.common.config.DatabaseSearchProperties()),
            new KeywordOptimizer(gateway),
            gateway,
            new StaticForumGuidanceProvider()
        );
        RecordingPublisher publisher = new RecordingPublisher();

        AgentState state = agent.runWorkflow(new AnalysisRequest(
            "task-3",
            "武汉大学樱花预约争议",
            Instant.parse("2026-03-18T00:00:00Z")
        ), publisher);

        assertEquals("INSIGHT", state.getAgentName());
        assertEquals("COMPLETED", state.getStatus());
        assertEquals("FinalizeReportNode", state.getCurrentNode());
        assertEquals(1, state.getRound());
        assertEquals(1, state.getParagraphs().size());
        assertEquals(2, state.getParagraphs().getFirst().getSearchHistory().size());
        assertEquals(1, state.getParagraphs().getFirst().getReflectionRoundsCompleted());
        assertEquals(3, state.getParagraphs().getFirst().getForumGuidanceRevisionApplied());
        assertTrue(state.getParagraphs().getFirst().getForumGuidancePrompt().contains("群体差异"));
        assertTrue(state.getParagraphs().getFirst().getSearchHistory().get(1).getSearchQuery().contains("群体差异"));
        assertTrue(state.getParagraphs().getFirst().getSearchHistory().get(1).getSearchQuery().contains("吐槽"));
        assertTrue(state.getFinalReport().contains("最终结论"));
        assertEquals(2, publisher.events().stream().filter(ToolCalledEvent.class::isInstance).count());
        assertTrue(publisher.events().stream().anyMatch(NodeStartedEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(DeltaChunkEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(AgentSpeechEvent.class::isInstance));
    }

    @Test
    void stopsWorkflowWhenStructuredDecisionRejectsRefinement() {
        NoRefineDecisionGateway gateway = new NoRefineDecisionGateway();
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("NEUTRAL", 0.62, true),
            new com.bettafish.insight.tool.MediaCrawlerDbTool(null, new com.bettafish.common.config.DatabaseSearchProperties()),
            new KeywordOptimizer(gateway),
            gateway,
            new StaticForumGuidanceProvider()
        );

        AgentState state = agent.runWorkflow(new AnalysisRequest(
            "task-4",
            "武汉大学樱花预约争议",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals(1, state.getParagraphs().getFirst().getSearchHistory().size());
        assertEquals(0, state.getParagraphs().getFirst().getReflectionRoundsCompleted());
        assertTrue(gateway.decisionPromptSeen());
    }

    private static final class DeterministicKeywordGateway implements LlmGateway {

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            if (responseType.getSimpleName().equals("ReflectionDecisionResponse")) {
                return responseType.cast(new InsightAgent.ReflectionDecisionResponse(
                    true,
                    "武汉大学樱花预约争议 群体差异 吐槽",
                    "need-more-complaint-samples"
                ));
            }
            if (responseType.getSimpleName().equals("StructuredNarrativeOutput")) {
                return instantiateStructuredNarrative(
                    responseType,
                    "首轮总结：正向情绪占优，围绕赏樱体验与评论热度的讨论集中。",
                    List.of("正向情绪占优", "评论热度持续攀升"),
                    List.of("负面样本还需补齐"),
                    "最终结论：正向体验驱动讨论扩散，但仍需关注排队与预约抱怨。"
                );
            }
            throw new UnsupportedOperationException("Unexpected class response type");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              com.fasterxml.jackson.core.type.TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            @SuppressWarnings("unchecked")
            T result = (T) java.util.List.of("武汉大学樱花太棒了", "武汉大学樱花太棒了 评论", "武汉大学樱花太棒了 热度");
            return result;
        }
    }

    private static final class RecordingInsightGateway implements LlmGateway {

        private final List<String> textPrompts = new ArrayList<>();
        private boolean summaryJsonSeen;
        private boolean reflectionSummaryJsonSeen;
        private boolean finalConclusionJsonSeen;

        @Override
        public String callText(String clientName, String systemPrompt, String userPrompt,
                               java.util.function.Supplier<String> fallbackSupplier) {
            textPrompts.add(userPrompt);
            return fallbackSupplier.get();
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            textPrompts.add(userPrompt);
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
                return responseType.cast(new InsightAgent.ReflectionDecisionResponse(
                    true,
                    "武汉大学樱花预约争议 群体差异 吐槽",
                    "need-more-complaint-samples"
                ));
            }
            if (responseType.getSimpleName().equals("StructuredNarrativeOutput")) {
                if (summaryJsonSeen && !reflectionSummaryJsonSeen) {
                    return instantiateStructuredNarrative(
                        responseType,
                        "首轮总结：预约争议主要集中在游客与学生群体，情绪整体中性但吐槽样本开始上升。",
                        List.of("游客与学生群体评价分化", "中性情绪中夹杂吐槽样本"),
                        List.of("预约吐槽样本仍需补齐"),
                        "阶段结论：群体差异是争议主轴。"
                    );
                }
                if (reflectionSummaryJsonSeen && !finalConclusionJsonSeen) {
                    return instantiateStructuredNarrative(
                        responseType,
                        "反思总结：游客与学生群体对预约机制的评价差异，被吐槽样本持续放大。",
                        List.of("预约机制引发群体认知差异", "吐槽样本放大排队与抢票焦虑"),
                        List.of("平台热度走势仍需补齐"),
                        "阶段结论：群体差异已被负面吐槽显著放大。"
                    );
                }
                return instantiateStructuredNarrative(
                    responseType,
                    "最终摘要：争议焦点集中在不同群体对预约公平性和体验成本的不同感知。",
                    List.of("游客与学生群体预期差异明显", "排队与抢票焦虑成为主要负向触发点"),
                    List.of("后续仍需补齐平台热度走势"),
                    "最终结论：预约争议的核心矛盾集中在游客与学生群体预期不一致，吐槽样本主要放大排队与抢票焦虑。"
                );
            }
            throw new UnsupportedOperationException("Unexpected class response type");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            @SuppressWarnings("unchecked")
            T result = (T) List.of("武汉大学樱花预约争议", "武汉大学樱花预约争议 评论", "武汉大学樱花预约争议 吐槽");
            return result;
        }

        List<String> textPrompts() {
            return textPrompts;
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

    private static final class StaticForumGuidanceProvider implements ForumGuidanceProvider {

        private final ForumGuidance guidance = new ForumGuidance(
            3,
            "主持人要求补齐预约争议样本",
            List.of("关注游客和学生群体差异"),
            List.of("不同群体为何产生分歧"),
            List.of("预约吐槽样本不足"),
            "请优先解释争议群体差异并补足预约吐槽样本"
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

    private static final class NoRefineDecisionGateway implements LlmGateway {

        private boolean decisionPromptSeen;
        private final List<String> textPrompts = new ArrayList<>();

        @Override
        public String callText(String clientName, String systemPrompt, String userPrompt,
                               java.util.function.Supplier<String> fallbackSupplier) {
            textPrompts.add(userPrompt);
            return fallbackSupplier.get();
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            textPrompts.add(userPrompt);
            if (responseType.getSimpleName().equals("ReflectionDecisionResponse")) {
                decisionPromptSeen = true;
                return fallbackSupplier.get();
            }
            if (responseType.getSimpleName().equals("StructuredNarrativeOutput")) {
                return instantiateStructuredNarrative(
                    responseType,
                    "首轮总结：预约争议主要集中在游客与学生群体。",
                    List.of("游客与学生群体评价分化", "预约吐槽开始累积"),
                    List.of("平台热度走势仍需补齐"),
                    "最终结论：争议主要来自群体预期不一致。"
                );
            }
            throw new UnsupportedOperationException("Unexpected class response type");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            @SuppressWarnings("unchecked")
            T result = (T) List.of("武汉大学樱花预约争议", "武汉大学樱花预约争议 评论", "武汉大学樱花预约争议 吐槽");
            return result;
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
