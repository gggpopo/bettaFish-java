package com.bettafish.media;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.bettafish.common.runtime.Node;
import com.bettafish.common.runtime.StateMachineRunner;
import com.bettafish.media.node.MediaNode;
import com.bettafish.media.node.MediaNodeContext;
import com.bettafish.media.prompt.MediaPrompts;
import com.bettafish.media.tool.BochaSearchTool;

@Service
public class MediaAgent implements AnalysisEngine {

    static final String MEDIA_CLIENT = "mediaChatClient";

    private final BochaSearchTool bochaSearchTool;
    private final LlmGateway llmGateway;
    private final ForumGuidanceProvider forumGuidanceProvider;
    private final StateMachineRunner<MediaNodeContext> runner = new StateMachineRunner<>();
    private final int maxReflections;

    @Autowired
    public MediaAgent(BochaSearchTool bochaSearchTool, LlmGateway llmGateway,
                      ObjectProvider<ForumGuidanceProvider> forumGuidanceProvider) {
        this(bochaSearchTool, llmGateway, forumGuidanceProvider.getIfAvailable(ForumGuidanceProvider::noop), 1);
    }

    MediaAgent(BochaSearchTool bochaSearchTool, LlmGateway llmGateway, ForumGuidanceProvider forumGuidanceProvider) {
        this(bochaSearchTool, llmGateway, forumGuidanceProvider, 1);
    }

    MediaAgent(BochaSearchTool bochaSearchTool, LlmGateway llmGateway, ForumGuidanceProvider forumGuidanceProvider,
               int maxReflections) {
        this.bochaSearchTool = bochaSearchTool;
        this.llmGateway = llmGateway;
        this.forumGuidanceProvider = forumGuidanceProvider;
        this.maxReflections = maxReflections;
    }

    public AgentState runWorkflow(AnalysisRequest request) {
        return runWorkflow(request, AnalysisEventPublisher.noop(), null);
    }

    public AgentState runWorkflow(AnalysisRequest request, AnalysisEventPublisher publisher) {
        return runWorkflow(request, publisher, null);
    }

    public AgentState runWorkflow(AnalysisRequest request, AnalysisEventPublisher publisher, ExecutionContext executionContext) {
        AgentState state = new AgentState("MEDIA", request.query());
        state.setStatus("RUNNING");
        MediaNodeContext context = new MediaNodeContext(this, request, state, maxReflections, publisher);
        if (executionContext != null) {
            context.putService(ExecutionContext.class, executionContext);
        }
        runner.run(context, MediaNode.PLAN_SEARCH);
        return state;
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
        AgentState state = runWorkflow(request, publisher, executionContext);
        ParagraphState paragraph = state.getParagraphs().getFirst();
        List<SourceReference> sources = paragraph.getSearchHistory().stream()
            .flatMap(searchRecord -> searchRecord.getSources().stream())
            .toList();
        return new EngineResult(
            EngineType.MEDIA,
            "Multimodal coverage for " + request.query(),
            state.getFinalReport(),
            List.of(
                "Estimated how image-heavy the topic appears",
                "Highlighted likely social sharing angles",
                "Captured placeholder structured facts for later enrichment"
            ),
            sources,
            Map.of(
                "mode", "bocha-tool",
                "workflow", "state-machine",
                "searchRounds", Integer.toString(paragraph.getSearchHistory().size()),
                "forumGuidanceRevision", Integer.toString(paragraph.getForumGuidanceRevisionApplied()),
                "forumGuidancePrompt", paragraph.getForumGuidancePrompt()
            )
        );
    }

    @Override
    public String engineName() {
        return EngineType.MEDIA.name();
    }

    public Node<MediaNodeContext> planSearch(MediaNodeContext context) {
        ParagraphState paragraph = new ParagraphState(
            "media-1",
            "多模态传播概览",
            "关注图文、短视频和视觉传播形态。"
        );
        context.getState().setParagraphs(List.of(paragraph));
        context.setPendingSearchQuery(context.getRequest().query());
        context.setPendingSearchReasoning("initial-media-search");
        syncForumState(context);
        return MediaNode.EXECUTE_SEARCH;
    }

    public Node<MediaNodeContext> executeInitialSearch(MediaNodeContext context) {
        return executeSearch(context, 0);
    }

    public Node<MediaNodeContext> summarizeFindings(MediaNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        ForumGuidance forumGuidance = syncForumState(context);
        String summary = llmGateway.callText(
            MEDIA_CLIENT,
            MediaPrompts.FIRST_SUMMARY_SYSTEM,
            MediaPrompts.buildFirstSummaryUserPrompt(context.getRequest().query(), latestSources(paragraph), forumGuidance),
            () -> defaultSummary(context.getRequest().query(), latestSources(paragraph))
        );
        paragraph.setCurrentDraft(summary);
        publishDelta(context, "media-summary", summary, paragraph.getSearchHistory().size());
        publishSpeech(context, summary);
        return MediaNode.REFLECT_ON_GAPS;
    }

    public Node<MediaNodeContext> reflectOnGaps(MediaNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        if (paragraph.getReflectionRoundsCompleted() >= context.getMaxReflections()) {
            return MediaNode.FINALIZE_REPORT;
        }

        ForumGuidance forumGuidance = syncForumState(context);
        if (forumGuidance == null) {
            return MediaNode.FINALIZE_REPORT;
        }

        ReflectionDecisionResponse response = llmGateway.callJson(
            MEDIA_CLIENT,
            MediaPrompts.REFLECTION_DECISION_SYSTEM,
            MediaPrompts.buildReflectionDecisionUserPrompt(
                context.getRequest().query(),
                paragraph.getCurrentDraft(),
                latestSources(paragraph),
                forumGuidance,
                context.getMaxReflections() - paragraph.getReflectionRoundsCompleted()
            ),
            ReflectionDecisionResponse.class,
            this::validateReflectionDecision,
            () -> defaultReflectionDecision(context.getRequest().query(), forumGuidance)
        );
        if (!response.shouldRefine()) {
            return MediaNode.FINALIZE_REPORT;
        }
        context.setPendingSearchQuery(response.searchQuery());
        context.setPendingSearchReasoning(response.reasoning());
        return MediaNode.REFINE_SEARCH;
    }

    public Node<MediaNodeContext> executeRefinementSearch(MediaNodeContext context) {
        return executeSearch(context, context.getParagraph().getReflectionRoundsCompleted() + 1);
    }

    public Node<MediaNodeContext> summarizeReflection(MediaNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        ForumGuidance forumGuidance = syncForumState(context);
        String reflectedSummary = llmGateway.callText(
            MEDIA_CLIENT,
            MediaPrompts.REFLECTION_SUMMARY_SYSTEM,
            MediaPrompts.buildReflectionSummaryUserPrompt(
                context.getRequest().query(),
                paragraph.getCurrentDraft(),
                latestSources(paragraph),
                forumGuidance
            ),
            () -> defaultReflectionSummary(paragraph.getCurrentDraft(), forumGuidance)
        );
        paragraph.setCurrentDraft(reflectedSummary);
        paragraph.setReflectionRoundsCompleted(paragraph.getReflectionRoundsCompleted() + 1);
        context.getState().setRound(paragraph.getReflectionRoundsCompleted());
        publishDelta(context, "media-reflection", reflectedSummary, paragraph.getSearchHistory().size());
        publishSpeech(context, reflectedSummary);
        return MediaNode.FINALIZE_REPORT;
    }

    public Node<MediaNodeContext> finalizeReport(MediaNodeContext context) {
        ParagraphState paragraph = context.getParagraph();
        paragraph.setCompleted(true);
        context.getState().setStatus("COMPLETED");
        context.getState().setFinalReport(paragraph.getCurrentDraft());
        return null;
    }

    private Node<MediaNodeContext> executeSearch(MediaNodeContext context, int roundIndex) {
        context.getEventPublisher().publish(new ToolCalledEvent(
            context.getRequest().taskId(),
            engineName(),
            "bochaSearch",
            context.getPendingSearchQuery(),
            context.getPendingSearchReasoning(),
            Instant.now()
        ));
        List<SourceReference> sources = bochaSearchTool.search(context.getPendingSearchQuery());
        context.getParagraph().addSearchRecord(new ParagraphState.SearchRecord(
            "bocha",
            context.getPendingSearchQuery(),
            context.getPendingSearchReasoning(),
            roundIndex,
            sources
        ));
        publishDelta(
            context,
            "media-sources",
            renderSourcesSnapshot(context.getPendingSearchQuery(), sources),
            context.getParagraph().getSearchHistory().size()
        );
        return roundIndex == 0 ? MediaNode.SUMMARIZE_FINDINGS : MediaNode.REFLECTION_SUMMARY;
    }

    private String defaultSummary(String query, List<SourceReference> sources) {
        return MediaPrompts.FIRST_SEARCH_SYSTEM + " " + query + "，共参考 " + sources.size() + " 条多模态来源。";
    }

    private String defaultReflectionSummary(String summary, ForumGuidance forumGuidance) {
        if (forumGuidance == null) {
            return summary;
        }
        return summary + " 主持指导补充：" + forumGuidance.promptAddendum() + "。";
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

    private ForumGuidance syncForumState(MediaNodeContext context) {
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

    private List<SourceReference> latestSources(ParagraphState paragraph) {
        if (paragraph.getSearchHistory().isEmpty()) {
            return List.of();
        }
        return paragraph.getSearchHistory().getLast().getSources();
    }

    private String buildRefinementQuery(String query, ForumGuidance forumGuidance) {
        List<String> parts = new ArrayList<>();
        parts.add(query);
        parts.addAll(forumGuidance.focusPoints());
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

    private String renderSourcesSnapshot(String query, List<SourceReference> sources) {
        StringBuilder builder = new StringBuilder();
        builder.append("query=").append(query);
        for (int index = 0; index < sources.size(); index++) {
            SourceReference source = sources.get(index);
            builder.append("\n[").append(index + 1).append("] ")
                .append(source.title()).append("\n")
                .append(source.url());
        }
        return builder.toString();
    }

    private void publishDelta(MediaNodeContext context, String channel, String content, int sequence) {
        context.getEventPublisher().publish(new DeltaChunkEvent(
            context.getRequest().taskId(),
            engineName(),
            channel,
            content,
            sequence,
            Instant.now()
        ));
    }

    private void publishSpeech(MediaNodeContext context, String content) {
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
