package com.bettafish.insight;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ForumGuidanceProvider;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.ToolCalledEvent;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ParagraphState;
import com.bettafish.common.model.StructuredNarrativeMetadata;
import com.bettafish.common.model.StructuredNarrativeOutput;
import com.bettafish.common.runtime.Node;
import com.bettafish.common.runtime.StateMachineRunner;
import com.bettafish.insight.keyword.KeywordOptimizer;
import com.bettafish.insight.node.InsightNode;
import com.bettafish.insight.node.InsightNodeContext;
import com.bettafish.insight.prompt.InsightPrompts;
import com.bettafish.insight.tool.MediaCrawlerDbTool;
import com.bettafish.insight.tool.SentimentTool;

@Service
public class InsightAgent implements AnalysisEngine {

    static final String INSIGHT_CLIENT = "insightChatClient";

    private final SentimentAnalysisClient sentimentAnalysisClient;
    private final MediaCrawlerDbTool mediaCrawlerDbTool;
    private final KeywordOptimizer keywordOptimizer;
    private final LlmGateway llmGateway;
    private final ForumGuidanceProvider forumGuidanceProvider;
    private final StateMachineRunner<InsightNodeContext> runner = new StateMachineRunner<>();
    private final int maxReflections;

    @Autowired
    public InsightAgent(
        SentimentTool sentimentTool,
        MediaCrawlerDbTool mediaCrawlerDbTool,
        KeywordOptimizer keywordOptimizer,
        LlmGateway llmGateway,
        ObjectProvider<ForumGuidanceProvider> forumGuidanceProvider
    ) {
        this(
            sentimentTool::analyze,
            mediaCrawlerDbTool,
            keywordOptimizer,
            llmGateway,
            forumGuidanceProvider.getIfAvailable(ForumGuidanceProvider::noop),
            1
        );
    }

    InsightAgent(SentimentAnalysisClient sentimentAnalysisClient) {
        this(
            sentimentAnalysisClient,
            new MediaCrawlerDbTool(null, new com.bettafish.common.config.DatabaseSearchProperties()),
            defaultKeywordOptimizer(defaultLlmGateway()),
            defaultLlmGateway(),
            ForumGuidanceProvider.noop(),
            1
        );
    }

    InsightAgent(
        SentimentAnalysisClient sentimentAnalysisClient,
        MediaCrawlerDbTool mediaCrawlerDbTool,
        KeywordOptimizer keywordOptimizer
    ) {
        this(sentimentAnalysisClient, mediaCrawlerDbTool, keywordOptimizer, defaultLlmGateway(), ForumGuidanceProvider.noop(), 1);
    }

    InsightAgent(
        SentimentAnalysisClient sentimentAnalysisClient,
        MediaCrawlerDbTool mediaCrawlerDbTool,
        KeywordOptimizer keywordOptimizer,
        LlmGateway llmGateway,
        ForumGuidanceProvider forumGuidanceProvider
    ) {
        this(sentimentAnalysisClient, mediaCrawlerDbTool, keywordOptimizer, llmGateway, forumGuidanceProvider, 1);
    }

    InsightAgent(
        SentimentAnalysisClient sentimentAnalysisClient,
        MediaCrawlerDbTool mediaCrawlerDbTool,
        KeywordOptimizer keywordOptimizer,
        LlmGateway llmGateway,
        ForumGuidanceProvider forumGuidanceProvider,
        int maxReflections
    ) {
        this.sentimentAnalysisClient = sentimentAnalysisClient;
        this.mediaCrawlerDbTool = mediaCrawlerDbTool;
        this.keywordOptimizer = keywordOptimizer;
        this.llmGateway = llmGateway;
        this.forumGuidanceProvider = forumGuidanceProvider;
        this.maxReflections = maxReflections;
    }

    private static KeywordOptimizer defaultKeywordOptimizer(LlmGateway llmGateway) {
        return new KeywordOptimizer(llmGateway);
    }

    private static LlmGateway defaultLlmGateway() {
        return new LlmGateway() {
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
                return fallbackSupplier.get();
            }
        };
    }

    @Override
    public EngineResult analyze(AnalysisRequest request) {
        return analyze(request, AnalysisEventPublisher.noop(), null);
    }

    @Override
    public EngineResult analyze(AnalysisRequest request, AnalysisEventPublisher publisher) {
        return analyze(request, publisher, null);
    }

    @Override
    public EngineResult analyze(AnalysisRequest request, AnalysisEventPublisher publisher, ExecutionContext executionContext) {
        SentimentSignal sentiment = sentimentAnalysisClient.analyze(request.query());
        AgentState state = runWorkflow(request, publisher, executionContext, sentiment);
        ParagraphState paragraph = state.getParagraphs().getFirst();
        String sentimentLabel = sentiment.label();
        String sentimentConfidence = String.format(Locale.ROOT, "%.2f", sentiment.confidence());
        String mode = sentiment.enabled() ? "sentiment-mcp" : "sentiment-disabled";
        List<SourceReference> sources = paragraph.getSearchHistory().stream()
            .flatMap(searchRecord -> searchRecord.getSources().stream())
            .toList();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("mode", mode);
        metadata.put("workflow", "state-machine");
        metadata.put("searchRounds", Integer.toString(paragraph.getSearchHistory().size()));
        metadata.put("sentimentLabel", sentimentLabel);
        metadata.put("sentimentConfidence", sentimentConfidence);
        metadata.put("forumGuidanceRevision", Integer.toString(paragraph.getForumGuidanceRevisionApplied()));
        metadata.put("forumGuidancePrompt", paragraph.getForumGuidancePrompt());
        metadata.put(StructuredNarrativeMetadata.FORMAT, StructuredNarrativeMetadata.FORMAT_V1);
        metadata.put(StructuredNarrativeMetadata.DRAFT_SUMMARY, paragraph.getCurrentDraft());
        metadata.put(StructuredNarrativeMetadata.FINAL_CONCLUSION, paragraph.getFinalConclusion());
        metadata.put(StructuredNarrativeMetadata.EVIDENCE_GAPS, String.join("；", paragraph.getCurrentEvidenceGaps()));

        return new EngineResult(
            EngineType.INSIGHT,
            "Audience sentiment around " + request.query(),
            paragraph.getFinalConclusion().isBlank() ? state.getFinalReport() : paragraph.getFinalConclusion(),
            paragraph.getCurrentKeyPoints().isEmpty()
                ? List.of(
                    "Dominant sentiment: " + sentimentLabel,
                    "Sentiment confidence: " + sentimentConfidence,
                    "Optimized keywords: " + String.join(", ", latestKeywords(paragraph))
                )
                : List.copyOf(paragraph.getCurrentKeyPoints()),
            sources,
            Map.copyOf(metadata)
        );
    }

    @Override
    public String engineName() {
        return EngineType.INSIGHT.name();
    }

    public AgentState runWorkflow(AnalysisRequest request) {
        return runWorkflow(request, AnalysisEventPublisher.noop(), null);
    }

    public AgentState runWorkflow(AnalysisRequest request, AnalysisEventPublisher publisher) {
        return runWorkflow(request, publisher, null);
    }

    public AgentState runWorkflow(AnalysisRequest request, AnalysisEventPublisher publisher, ExecutionContext executionContext) {
        return runWorkflow(request, publisher, executionContext, sentimentAnalysisClient.analyze(request.query()));
    }

    private AgentState runWorkflow(AnalysisRequest request, AnalysisEventPublisher publisher,
                                   ExecutionContext executionContext, SentimentSignal sentiment) {
        AgentState state = new AgentState("INSIGHT", request.query());
        state.setStatus("RUNNING");
        InsightNodeContext context = new InsightNodeContext(this, request, state, sentiment, maxReflections, publisher);
        if (executionContext != null) {
            context.putService(ExecutionContext.class, executionContext);
        }
        runner.run(context, InsightNode.PLAN_SEARCH);
        return state;
    }

    public Node<InsightNodeContext> planSearch(InsightNodeContext context) {
        ParagraphState paragraph = new ParagraphState(
            "insight-1",
            "社交舆情观察",
            "围绕情绪、争议群体和讨论焦点总结社交反馈。"
        );
        context.getState().setParagraphs(List.of(paragraph));
        context.setPendingSearchQuery(context.getRequest().query());
        context.setPendingSearchReasoning("initial-insight-search");
        syncForumState(context);
        return InsightNode.EXECUTE_SEARCH;
    }

    public Node<InsightNodeContext> executeInitialSearch(InsightNodeContext context) {
        return executeSearch(context, 0);
    }

    public Node<InsightNodeContext> summarizeFindings(InsightNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        ForumGuidance forumGuidance = syncForumState(context);
        StructuredNarrativeOutput response = llmGateway.callJson(
            INSIGHT_CLIENT,
            InsightPrompts.FIRST_SUMMARY_SYSTEM,
            InsightPrompts.buildFirstSummaryUserPrompt(
                context.getRequest().query(),
                context.getSentimentSignal(),
                context.getLatestKeywords(),
                latestSources(paragraph),
                forumGuidance
            ),
            StructuredNarrativeOutput.class,
            this::validateStructuredNarrative,
            () -> defaultSummary(
                context.getRequest().query(),
                context.getSentimentSignal(),
                String.format(Locale.ROOT, "%.2f", context.getSentimentSignal().confidence()),
                context.getLatestKeywords()
            )
        );
        applyStructuredNarrative(paragraph, response);
        publishDelta(context, "insight-summary", response.summary(), paragraph.getSearchHistory().size());
        publishSpeech(context, response.summary());
        return InsightNode.REFLECT_ON_GAPS;
    }

    public Node<InsightNodeContext> reflectOnGaps(InsightNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        if (paragraph.getReflectionRoundsCompleted() >= context.getMaxReflections()) {
            return InsightNode.FINALIZE_REPORT;
        }

        ForumGuidance forumGuidance = syncForumState(context);
        if (forumGuidance == null) {
            return InsightNode.FINALIZE_REPORT;
        }

        ReflectionDecisionResponse response = llmGateway.callJson(
            INSIGHT_CLIENT,
            InsightPrompts.REFLECTION_DECISION_SYSTEM,
            InsightPrompts.buildReflectionDecisionUserPrompt(
                context.getRequest().query(),
                paragraph.getCurrentDraft(),
                context.getSentimentSignal(),
                context.getLatestKeywords(),
                latestSources(paragraph),
                forumGuidance,
                context.getMaxReflections() - paragraph.getReflectionRoundsCompleted()
            ),
            ReflectionDecisionResponse.class,
            this::validateReflectionDecision,
            () -> defaultReflectionDecision(context.getRequest().query(), forumGuidance)
        );
        if (!response.shouldRefine()) {
            return InsightNode.FINALIZE_REPORT;
        }
        context.setPendingSearchQuery(response.searchQuery());
        context.setPendingSearchReasoning(response.reasoning());
        return InsightNode.REFINE_SEARCH;
    }

    public Node<InsightNodeContext> executeRefinementSearch(InsightNodeContext context) {
        return executeSearch(context, context.getParagraph().getReflectionRoundsCompleted() + 1);
    }

    public Node<InsightNodeContext> summarizeReflection(InsightNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        ForumGuidance forumGuidance = syncForumState(context);
        StructuredNarrativeOutput response = llmGateway.callJson(
            INSIGHT_CLIENT,
            InsightPrompts.REFLECTION_SUMMARY_SYSTEM,
            InsightPrompts.buildReflectionSummaryUserPrompt(
                context.getRequest().query(),
                paragraph.getCurrentDraft(),
                context.getSentimentSignal(),
                context.getLatestKeywords(),
                latestSources(paragraph),
                forumGuidance
            ),
            StructuredNarrativeOutput.class,
            this::validateStructuredNarrative,
            () -> defaultReflectionSummary(paragraph.getCurrentDraft(), forumGuidance)
        );
        applyStructuredNarrative(paragraph, response);
        paragraph.setReflectionRoundsCompleted(paragraph.getReflectionRoundsCompleted() + 1);
        context.getState().setRound(paragraph.getReflectionRoundsCompleted());
        publishDelta(context, "insight-reflection", response.summary(), paragraph.getSearchHistory().size());
        publishSpeech(context, response.summary());
        return InsightNode.FINALIZE_REPORT;
    }

    public Node<InsightNodeContext> finalizeReport(InsightNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        ForumGuidance forumGuidance = syncForumState(context);
        StructuredNarrativeOutput response = llmGateway.callJson(
            INSIGHT_CLIENT,
            InsightPrompts.FINAL_CONCLUSION_SYSTEM,
            InsightPrompts.buildFinalConclusionUserPrompt(
                context.getRequest().query(),
                paragraph.getCurrentDraft(),
                context.getSentimentSignal(),
                context.getLatestKeywords(),
                paragraph.getCurrentKeyPoints(),
                paragraph.getCurrentEvidenceGaps(),
                latestSources(paragraph),
                forumGuidance
            ),
            StructuredNarrativeOutput.class,
            this::validateStructuredNarrative,
            () -> defaultFinalConclusion(context, paragraph, forumGuidance)
        );
        applyStructuredNarrative(paragraph, response);
        paragraph.setCompleted(true);
        context.getState().setStatus("COMPLETED");
        context.getState().setFinalReport(response.finalConclusion());
        publishDelta(context, "insight-final-conclusion", response.finalConclusion(), paragraph.getSearchHistory().size() + 1);
        publishSpeech(context, response.finalConclusion());
        return null;
    }

    private StructuredNarrativeOutput defaultSummary(String query, SentimentSignal sentiment, String sentimentConfidence,
                                                     List<String> optimizedKeywords) {
        return new StructuredNarrativeOutput(
            "InsightAgent produced a social sentiment snapshot for " + query
                + " with dominant sentiment " + sentiment.label()
                + " (confidence " + sentimentConfidence + ")."
                + " Optimized keywords: " + String.join(", ", optimizedKeywords) + ".",
            List.of(
                "Dominant sentiment: " + sentiment.label(),
                "Sentiment confidence: " + sentimentConfidence
            ),
            List.of(),
            "最终结论：" + query + " 当前以 " + sentiment.label() + " 情绪占主导。"
        );
    }

    private StructuredNarrativeOutput defaultReflectionSummary(String summary, ForumGuidance forumGuidance) {
        String supplement = forumGuidance == null ? "" : " 主持指导补充：" + forumGuidance.promptAddendum() + "。";
        List<String> evidenceGaps = (forumGuidance == null || forumGuidance.promptAddendum() == null
            || forumGuidance.promptAddendum().isBlank()) ? List.of() : List.of(forumGuidance.promptAddendum());
        return new StructuredNarrativeOutput(
            summary + supplement,
            List.of(
                "已根据主持指导补充群体差异",
                "需要继续追踪争议来源和证据缺口"
            ),
            evidenceGaps,
            "最终结论：" + summary + supplement
        );
    }

    private StructuredNarrativeOutput defaultFinalConclusion(InsightNodeContext context,
                                                             ParagraphState paragraph,
                                                             ForumGuidance forumGuidance) {
        String conclusion = paragraph.getFinalConclusion();
        if (conclusion == null || conclusion.isBlank()) {
            conclusion = "最终结论：" + context.getRequest().query() + " 的舆情焦点仍需继续跟踪。";
        }
        if (forumGuidance != null && forumGuidance.promptAddendum() != null && !forumGuidance.promptAddendum().isBlank()) {
            conclusion = conclusion + " 主持指导重点：" + forumGuidance.promptAddendum() + "。";
        }
        return new StructuredNarrativeOutput(
            paragraph.getCurrentDraft(),
            paragraph.getCurrentKeyPoints().isEmpty()
                ? List.of(
                    "Dominant sentiment: " + context.getSentimentSignal().label(),
                    "Optimized keywords: " + String.join(", ", context.getLatestKeywords())
                )
                : paragraph.getCurrentKeyPoints(),
            paragraph.getCurrentEvidenceGaps(),
            conclusion
        );
    }

    private void applyStructuredNarrative(ParagraphState paragraph, StructuredNarrativeOutput response) {
        paragraph.setCurrentDraft(response.summary());
        paragraph.setCurrentKeyPoints(response.keyPoints());
        paragraph.setCurrentEvidenceGaps(response.evidenceGaps());
        paragraph.setFinalConclusion(response.finalConclusion());
    }

    private LlmGateway.ValidationResult validateStructuredNarrative(StructuredNarrativeOutput response) {
        if (response == null) {
            return LlmGateway.ValidationResult.invalid("structured narrative must not be null");
        }
        if (response.summary() == null || response.summary().isBlank()) {
            return LlmGateway.ValidationResult.invalid("summary must not be blank");
        }
        if (response.finalConclusion() == null || response.finalConclusion().isBlank()) {
            return LlmGateway.ValidationResult.invalid("final conclusion must not be blank");
        }
        if (response.keyPoints().isEmpty()) {
            return LlmGateway.ValidationResult.invalid("key points must not be empty");
        }
        boolean hasBlankKeyPoint = response.keyPoints().stream().anyMatch(value -> value == null || value.isBlank());
        if (hasBlankKeyPoint) {
            return LlmGateway.ValidationResult.invalid("key points must not contain blank entries");
        }
        boolean hasBlankGap = response.evidenceGaps().stream().anyMatch(value -> value == null || value.isBlank());
        if (hasBlankGap) {
            return LlmGateway.ValidationResult.invalid("evidence gaps must not contain blank entries");
        }
        return LlmGateway.ValidationResult.valid();
    }

    private LlmGateway.ValidationResult validateReflectionDecision(ReflectionDecisionResponse response) {
        if (response == null) {
            return LlmGateway.ValidationResult.invalid("reflection decision must not be null");
        }
        if (!response.shouldRefine()) {
            return LlmGateway.ValidationResult.valid();
        }
        if (response.searchQuery() == null || response.searchQuery().isBlank()) {
            return LlmGateway.ValidationResult.invalid("search query must not be blank when refinement is required");
        }
        if (response.reasoning() == null || response.reasoning().isBlank()) {
            return LlmGateway.ValidationResult.invalid("reasoning must not be blank when refinement is required");
        }
        return LlmGateway.ValidationResult.valid();
    }

    private Node<InsightNodeContext> executeSearch(InsightNodeContext context, int roundIndex) {
        List<String> optimizedKeywords = keywordOptimizer.optimize(context.getPendingSearchQuery());
        context.setLatestKeywords(optimizedKeywords);
        context.getEventPublisher().publish(new ToolCalledEvent(
            context.getRequest().taskId(),
            engineName(),
            "mediaCrawlerDbSearch",
            context.getPendingSearchQuery() + " | keywords=" + String.join(", ", optimizedKeywords),
            context.getPendingSearchReasoning(),
            Instant.now()
        ));
        List<SourceReference> sources = mediaCrawlerDbTool.search(context.getPendingSearchQuery(), optimizedKeywords);
        context.getParagraph().addSearchRecord(new ParagraphState.SearchRecord(
            "mediaCrawlerDb",
            context.getPendingSearchQuery(),
            context.getPendingSearchReasoning() + " | keywords=" + String.join(", ", optimizedKeywords),
            roundIndex,
            sources
        ));
        publishDelta(
            context,
            "insight-sources",
            renderSourcesSnapshot(context.getPendingSearchQuery(), sources, optimizedKeywords),
            context.getParagraph().getSearchHistory().size()
        );
        return roundIndex == 0 ? InsightNode.SUMMARIZE_FINDINGS : InsightNode.REFLECTION_SUMMARY;
    }

    private ForumGuidance syncForumState(InsightNodeContext context) {
        List<com.bettafish.common.model.ForumMessage> transcript = forumGuidanceProvider.transcript(context.getRequest().taskId());
        List<ForumGuidance> guidanceHistory = forumGuidanceProvider.guidanceHistory(context.getRequest().taskId());
        context.getState().setForumMessages(transcript);
        context.getState().setForumGuidanceHistory(guidanceHistory);

        ForumGuidance latestGuidance = guidanceHistory.isEmpty() ? null : guidanceHistory.getLast();
        if (latestGuidance != null) {
            ParagraphState paragraph = context.getParagraph();
            paragraph.setForumGuidanceRevisionApplied(latestGuidance.revision());
            paragraph.setForumGuidancePrompt(latestGuidance.promptAddendum());
        }
        return latestGuidance;
    }

    private String buildRefinementQuery(String query, ForumGuidance forumGuidance) {
        List<String> parts = new ArrayList<>();
        parts.add(query);
        parts.addAll(forumGuidance.focusPoints());
        parts.addAll(forumGuidance.challengeQuestions());
        parts.addAll(forumGuidance.evidenceGaps());
        if (forumGuidance.promptAddendum() != null && !forumGuidance.promptAddendum().isBlank()) {
            parts.add(forumGuidance.promptAddendum());
        }
        return String.join(" ", parts);
    }

    private ReflectionDecisionResponse defaultReflectionDecision(String query, ForumGuidance forumGuidance) {
        return new ReflectionDecisionResponse(
            false,
            buildRefinementQuery(query, forumGuidance),
            "fallback-default-stop-reflection"
        );
    }

    private List<SourceReference> latestSources(ParagraphState paragraph) {
        if (paragraph.getSearchHistory().isEmpty()) {
            return List.of();
        }
        return paragraph.getSearchHistory().getLast().getSources();
    }

    private List<String> latestKeywords(ParagraphState paragraph) {
        if (paragraph.getSearchHistory().isEmpty()) {
            return List.of();
        }
        String reasoning = paragraph.getSearchHistory().getLast().getReasoning();
        int keywordIndex = reasoning.indexOf("keywords=");
        if (keywordIndex < 0) {
            return List.of();
        }
        String keywordPart = reasoning.substring(keywordIndex + "keywords=".length());
        return List.of(keywordPart.split(",\\s*"));
    }

    private String renderSourcesSnapshot(String query, List<SourceReference> sources, List<String> optimizedKeywords) {
        StringBuilder builder = new StringBuilder();
        builder.append("query=").append(query)
            .append("\nkeywords=").append(String.join(", ", optimizedKeywords));
        for (int index = 0; index < sources.size(); index++) {
            SourceReference source = sources.get(index);
            builder.append("\n[").append(index + 1).append("] ")
                .append(source.title()).append("\n")
                .append(source.url());
        }
        return builder.toString();
    }

    private void publishDelta(InsightNodeContext context, String channel, String content, int sequence) {
        context.getEventPublisher().publish(new DeltaChunkEvent(
            context.getRequest().taskId(),
            engineName(),
            channel,
            content,
            sequence,
            Instant.now()
        ));
    }

    private void publishSpeech(InsightNodeContext context, String content) {
        context.getEventPublisher().publish(new AgentSpeechEvent(
            context.getRequest().taskId(),
            engineName(),
            content,
            Instant.now()
        ));
    }

    public record ReflectionDecisionResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("should_refine") boolean shouldRefine,
        @com.fasterxml.jackson.annotation.JsonProperty("search_query") String searchQuery,
        String reasoning
    ) {
    }
}
