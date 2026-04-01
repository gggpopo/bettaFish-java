package com.bettafish.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            new com.bettafish.insight.tool.MediaCrawlerDbTool(),
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
    }

    @Test
    void injectsForumGuidanceIntoInsightSummaryAndReflectionPrompts() {
        RecordingInsightGateway gateway = new RecordingInsightGateway();
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("NEUTRAL", 0.62, true),
            new com.bettafish.insight.tool.MediaCrawlerDbTool(),
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
        assertTrue(result.summary().contains("反思补充"));
    }

    @Test
    void runsGuidanceDrivenWorkflowAndRecordsRefinementSearch() {
        RecordingInsightGateway gateway = new RecordingInsightGateway();
        InsightAgent agent = new InsightAgent(
            text -> new SentimentSignal("NEUTRAL", 0.62, true),
            new com.bettafish.insight.tool.MediaCrawlerDbTool(),
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
        assertTrue(state.getFinalReport().contains("反思补充"));
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
            new com.bettafish.insight.tool.MediaCrawlerDbTool(),
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
        private int textCalls;

        @Override
        public String callText(String clientName, String systemPrompt, String userPrompt,
                               java.util.function.Supplier<String> fallbackSupplier) {
            textPrompts.add(userPrompt);
            textCalls++;
            if (textCalls == 1) {
                return "首轮总结：预约争议主要集中在游客与学生群体。";
            }
            return "反思补充：请优先解释争议群体差异并补足预约吐槽样本。";
        }

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
        private int textCalls;

        @Override
        public String callText(String clientName, String systemPrompt, String userPrompt,
                               java.util.function.Supplier<String> fallbackSupplier) {
            textPrompts.add(userPrompt);
            textCalls++;
            if (textCalls == 1) {
                return "首轮总结：预约争议主要集中在游客与学生群体。";
            }
            return "反思补充：请优先解释争议群体差异并补足预约吐槽样本。";
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            decisionPromptSeen = true;
            return fallbackSupplier.get();
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
