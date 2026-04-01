package com.bettafish.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ForumGuidanceProvider;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.ToolCalledEvent;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ParagraphState;
import com.bettafish.common.runtime.Node;
import com.bettafish.common.runtime.StateMachineRunner;
import com.bettafish.query.node.QueryNode;
import com.bettafish.query.node.QueryNodeContext;
import com.bettafish.query.prompt.QueryPrompts;
import com.bettafish.query.tool.TavilySearchTool;

@Service
public class QueryAgent implements AnalysisEngine {

    static final String QUERY_CLIENT = "queryChatClient";

    private final TavilySearchTool tavilySearchTool;
    private final LlmGateway llmGateway;
    private final ForumGuidanceProvider forumGuidanceProvider;
    private final StateMachineRunner<QueryNodeContext> runner = new StateMachineRunner<>();
    private final int maxReflections;
    private final int maxParagraphs;

    @Autowired
    public QueryAgent(TavilySearchTool tavilySearchTool, LlmGateway llmGateway,
                      ObjectProvider<ForumGuidanceProvider> forumGuidanceProvider) {
        this(tavilySearchTool, llmGateway, forumGuidanceProvider.getIfAvailable(ForumGuidanceProvider::noop), 3, 5);
    }

    QueryAgent(TavilySearchTool tavilySearchTool, LlmGateway llmGateway, int maxReflections, int maxParagraphs) {
        this(tavilySearchTool, llmGateway, ForumGuidanceProvider.noop(), maxReflections, maxParagraphs);
    }

    QueryAgent(TavilySearchTool tavilySearchTool, LlmGateway llmGateway, ForumGuidanceProvider forumGuidanceProvider,
               int maxReflections, int maxParagraphs) {
        this.tavilySearchTool = tavilySearchTool;
        this.llmGateway = llmGateway;
        this.forumGuidanceProvider = forumGuidanceProvider;
        this.maxReflections = maxReflections;
        this.maxParagraphs = maxParagraphs;
    }

    public AgentState runWorkflow(AnalysisRequest request) {
        return runWorkflow(request, AnalysisEventPublisher.noop(), null);
    }

    public AgentState runWorkflow(AnalysisRequest request, AnalysisEventPublisher publisher) {
        return runWorkflow(request, publisher, null);
    }

    public AgentState runWorkflow(AnalysisRequest request, AnalysisEventPublisher publisher, ExecutionContext executionContext) {
        AgentState state = new AgentState("QUERY", request.query());
        state.setStatus("RUNNING");
        QueryNodeContext context = new QueryNodeContext(this, request, state, maxReflections, maxParagraphs, publisher);
        context.putService(LlmGateway.class, llmGateway);
        context.putService(TavilySearchTool.class, tavilySearchTool);
        context.putService(ForumGuidanceProvider.class, forumGuidanceProvider);
        if (executionContext != null) {
            context.putService(ExecutionContext.class, executionContext);
        }
        runner.run(context, QueryNode.PLAN_PARAGRAPHS);
        return state;
    }

    @Override
    public EngineResult analyze(AnalysisRequest request) {
        return analyze(request, AnalysisEventPublisher.noop());
    }

    @Override
    public EngineResult analyze(AnalysisRequest request, AnalysisEventPublisher publisher) {
        return analyze(request, publisher, null);
    }

    @Override
    public EngineResult analyze(AnalysisRequest request,
                                AnalysisEventPublisher publisher,
                                ExecutionContext executionContext) {
        AgentState state = runWorkflow(request, publisher, executionContext);
        List<SourceReference> sources = state.getParagraphs().stream()
            .flatMap(paragraph -> paragraph.getSearchHistory().stream())
            .flatMap(searchRecord -> searchRecord.getSources().stream())
            .toList();
        return new EngineResult(
            EngineType.QUERY,
            "News pulse for " + request.query(),
            state.getFinalReport(),
            state.getParagraphs().stream().map(ParagraphState::getTitle).toList(),
            sources,
            Map.of(
                "mode", "state-machine",
                "paragraphCount", Integer.toString(state.getParagraphs().size()),
                "reflectionCount", Integer.toString(maxReflections),
                "currentNode", state.getCurrentNode()
            )
        );
    }

    @Override
    public String engineName() {
        return EngineType.QUERY.name();
    }

    public Node<QueryNodeContext> planParagraphs(QueryNodeContext context) {
        List<ParagraphPlan> plannedParagraphs = llmGateway.callJson(
            QUERY_CLIENT,
            QueryPrompts.REPORT_STRUCTURE_SYSTEM,
            QueryPrompts.buildReportStructureUserPrompt(context.getRequest().query(), context.getMaxParagraphs()),
            new TypeReference<List<ParagraphPlan>>() {
            },
            this::validateParagraphPlans,
            () -> defaultParagraphPlans(context.getRequest().query(), context.getMaxParagraphs())
        );

        List<ParagraphState> paragraphs = new ArrayList<>();
        for (int index = 0; index < plannedParagraphs.size() && index < context.getMaxParagraphs(); index++) {
            ParagraphPlan plan = plannedParagraphs.get(index);
            paragraphs.add(new ParagraphState(
                "paragraph-" + (index + 1),
                plan.title(),
                plan.expectedContent()
            ));
        }

        context.getState().setParagraphs(paragraphs);
        context.setCurrentParagraphIndex(0);
        return paragraphs.isEmpty() ? QueryNode.FORMAT_REPORT : QueryNode.FIRST_SEARCH_DECISION;
    }

    public Node<QueryNodeContext> decideFirstSearch(QueryNodeContext context) {
        ParagraphState paragraph = context.getCurrentParagraph();
        String searchQuery = context.getRequest().query() + " " + paragraph.getTitle() + " " + paragraph.getExpectedContent();
        context.setPendingSearchQuery(searchQuery);
        context.setPendingSearchReasoning("initial-search");
        context.setPendingReflectionRound(-1);
        context.setPendingSummaryStage(QueryNodeContext.SummaryStage.FIRST);
        return QueryNode.TOOL_EXECUTE;
    }

    public Node<QueryNodeContext> executeTool(QueryNodeContext context) {
        context.getEventPublisher().publish(new ToolCalledEvent(
            context.getRequest().taskId(),
            engineName(),
            "tavilySearch",
            context.getPendingSearchQuery(),
            context.getPendingSearchReasoning(),
            Instant.now()
        ));
        ParagraphState paragraph = context.getCurrentParagraph();
        List<SourceReference> sources = tavilySearchTool.search(context.getPendingSearchQuery());
        paragraph.addSearchRecord(new ParagraphState.SearchRecord(
            "tavily",
            context.getPendingSearchQuery(),
            context.getPendingSearchReasoning(),
            context.getPendingReflectionRound(),
            sources
        ));
        publishDelta(
            context,
            "search-sources",
            renderSearchSourcesSnapshot(context.getPendingSearchQuery(), sources),
            paragraph.getSearchHistory().size()
        );
        return context.getPendingSummaryStage() == QueryNodeContext.SummaryStage.FIRST
            ? QueryNode.FIRST_SUMMARY
            : QueryNode.REFLECTION_SUMMARY;
    }

    public Node<QueryNodeContext> summarizeFirstSearch(QueryNodeContext context) {
        ParagraphState paragraph = context.getCurrentParagraph();
        ParagraphState.SearchRecord lastSearch = paragraph.getSearchHistory().getLast();
        String evidence = renderEvidence(lastSearch.getSources());
        ForumGuidance forumGuidance = syncForumState(context);
        ParagraphSummaryResponse response = llmGateway.callJson(
            QUERY_CLIENT,
            QueryPrompts.FIRST_SUMMARY_SYSTEM,
            QueryPrompts.buildFirstSummaryUserPrompt(context.getRequest().query(), paragraph, evidence, forumGuidance),
            ParagraphSummaryResponse.class,
            this::validateParagraphSummary,
            () -> defaultParagraphSummary(context, paragraph, evidence)
        );
        paragraph.setCurrentDraft(response.paragraphLatestState());
        publishDelta(context, "paragraph-summary", response.paragraphLatestState(), paragraph.getSearchHistory().size());
        publishSpeech(context, response.paragraphLatestState());
        syncForumState(context);
        return QueryNode.REFLECTION_DECISION;
    }

    public Node<QueryNodeContext> decideReflection(QueryNodeContext context) {
        ParagraphState paragraph = context.getCurrentParagraph();
        if (paragraph.getReflectionRoundsCompleted() >= context.getMaxReflections()) {
            return moveToNextParagraphOrFinalize(context);
        }

        ForumGuidance forumGuidance = syncForumState(context);
        ReflectionDecisionResponse response = llmGateway.callJson(
            QUERY_CLIENT,
            QueryPrompts.REFLECTION_DECISION_SYSTEM,
            QueryPrompts.buildReflectionDecisionUserPrompt(
                context.getRequest().query(),
                paragraph,
                context.getMaxReflections() - paragraph.getReflectionRoundsCompleted(),
                forumGuidance
            ),
            ReflectionDecisionResponse.class,
            this::validateReflectionDecision,
            this::defaultReflectionDecision
        );

        if (!response.shouldRefine()) {
            return moveToNextParagraphOrFinalize(context);
        }

        context.setPendingReflectionRound(paragraph.getReflectionRoundsCompleted());
        context.setPendingSearchQuery(response.searchQuery());
        context.setPendingSearchReasoning(response.reasoning());
        context.setPendingSummaryStage(QueryNodeContext.SummaryStage.REFLECTION);
        return QueryNode.TOOL_EXECUTE;
    }

    public Node<QueryNodeContext> summarizeReflection(QueryNodeContext context) {
        ParagraphState paragraph = context.getCurrentParagraph();
        ParagraphState.SearchRecord lastSearch = paragraph.getSearchHistory().getLast();
        String evidence = renderEvidence(lastSearch.getSources());
        ForumGuidance forumGuidance = syncForumState(context);
        int reflectionRound = lastSearch.getRoundIndex() + 1;
        ReflectionSummaryResponse response = llmGateway.callJson(
            QUERY_CLIENT,
            QueryPrompts.REFLECTION_SUMMARY_SYSTEM,
            QueryPrompts.buildReflectionSummaryUserPrompt(context.getRequest().query(), paragraph, evidence, forumGuidance),
            ReflectionSummaryResponse.class,
            this::validateReflectionSummary,
            () -> defaultReflectionSummary(paragraph, evidence)
        );
        paragraph.setCurrentDraft(response.updatedParagraphLatestState());
        paragraph.setReflectionRoundsCompleted(reflectionRound);
        context.getState().setRound(Math.max(context.getState().getRound(), reflectionRound));
        publishDelta(context, "reflection-summary", response.updatedParagraphLatestState(), reflectionRound + 1);
        publishSpeech(context, response.updatedParagraphLatestState());
        syncForumState(context);
        return QueryNode.REFLECTION_DECISION;
    }

    public Node<QueryNodeContext> formatReport(QueryNodeContext context) {
        FinalReportResponse response = llmGateway.callJson(
            QUERY_CLIENT,
            QueryPrompts.FINAL_REPORT_SYSTEM,
            QueryPrompts.buildFinalReportUserPrompt(context.getRequest().query(), context.getState().getParagraphs()),
            FinalReportResponse.class,
            this::validateFinalReport,
            () -> defaultFinalReport(context)
        );
        context.getState().setFinalReport(response.finalReport());
        context.getState().setStatus("COMPLETED");
        context.getState().setRound(Math.max(context.getState().getRound(), maxReflections));
        publishDelta(context, "final-report", response.finalReport(), context.getState().getParagraphs().size() + 1);
        return null;
    }

    private Node<QueryNodeContext> moveToNextParagraphOrFinalize(QueryNodeContext context) {
        ParagraphState paragraph = context.getCurrentParagraph();
        paragraph.setCompleted(true);
        int nextParagraph = context.getCurrentParagraphIndex() + 1;
        if (nextParagraph < context.getState().getParagraphs().size()) {
            context.setCurrentParagraphIndex(nextParagraph);
            context.setPendingReflectionRound(-1);
            context.setPendingSearchQuery("");
            context.setPendingSearchReasoning("");
            context.setPendingSummaryStage(QueryNodeContext.SummaryStage.FIRST);
            return QueryNode.FIRST_SEARCH_DECISION;
        }
        return QueryNode.FORMAT_REPORT;
    }

    private String renderEvidence(List<SourceReference> sources) {
        return sources.stream()
            .map(source -> source.title() + "：" + source.snippet())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("暂无搜索证据。");
    }

    private String renderSearchSourcesSnapshot(String query, List<SourceReference> sources) {
        if (sources.isEmpty()) {
            return "检索查询：" + query + "\n未获取到可用来源。";
        }
        StringBuilder builder = new StringBuilder("检索查询：").append(query).append('\n');
        for (int index = 0; index < sources.size(); index++) {
            SourceReference source = sources.get(index);
            builder.append('[').append(index + 1).append("] ")
                .append(source.title()).append('\n')
                .append(source.url()).append('\n');
            if (source.snippet() != null && !source.snippet().isBlank()) {
                builder.append(source.snippet()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private void publishDelta(QueryNodeContext context, String channel, String content, int sequence) {
        context.getEventPublisher().publish(new DeltaChunkEvent(
            context.getRequest().taskId(),
            engineName(),
            channel,
            content,
            sequence,
            Instant.now()
        ));
    }

    private void publishSpeech(QueryNodeContext context, String content) {
        context.getEventPublisher().publish(new AgentSpeechEvent(
            context.getRequest().taskId(),
            engineName(),
            content,
            Instant.now()
        ));
    }

    private ForumGuidance syncForumState(QueryNodeContext context) {
        List<com.bettafish.common.model.ForumMessage> transcript = forumGuidanceProvider.transcript(context.getRequest().taskId());
        List<ForumGuidance> guidanceHistory = forumGuidanceProvider.guidanceHistory(context.getRequest().taskId());
        context.getState().setForumMessages(transcript);
        context.getState().setForumGuidanceHistory(guidanceHistory);

        ForumGuidance latestGuidance = guidanceHistory.isEmpty() ? null : guidanceHistory.getLast();
        if (latestGuidance != null && context.getCurrentParagraphIndex() >= 0
            && context.getCurrentParagraphIndex() < context.getState().getParagraphs().size()) {
            ParagraphState paragraph = context.getCurrentParagraph();
            paragraph.setForumGuidanceRevisionApplied(latestGuidance.revision());
            paragraph.setForumGuidancePrompt(latestGuidance.promptAddendum());
        }
        return latestGuidance;
    }

    private List<ParagraphPlan> defaultParagraphPlans(String query, int maxParagraphCount) {
        List<ParagraphPlan> defaults = new ArrayList<>();
        for (int index = 0; index < maxParagraphCount; index++) {
            String title = QueryPrompts.DEFAULT_SECTION_TITLES.get(index % QueryPrompts.DEFAULT_SECTION_TITLES.size());
            String focus = QueryPrompts.DEFAULT_SECTION_FOCUS.get(index % QueryPrompts.DEFAULT_SECTION_FOCUS.size());
            defaults.add(new ParagraphPlan(title, "围绕“" + query + "”" + focus + "。"));
        }
        return defaults;
    }

    private LlmGateway.ValidationResult validateParagraphPlans(List<ParagraphPlan> plannedParagraphs) {
        if (plannedParagraphs == null || plannedParagraphs.isEmpty()) {
            return LlmGateway.ValidationResult.invalid("paragraph plans must not be empty");
        }
        boolean hasInvalidPlan = plannedParagraphs.stream()
            .anyMatch(plan -> plan == null || isBlank(plan.title()) || isBlank(plan.expectedContent()));
        if (hasInvalidPlan) {
            return LlmGateway.ValidationResult.invalid("paragraph plans must include non-blank title and content");
        }
        return LlmGateway.ValidationResult.valid();
    }

    private LlmGateway.ValidationResult validateParagraphSummary(ParagraphSummaryResponse response) {
        return validateText("paragraph_latest_state", response == null ? null : response.paragraphLatestState());
    }

    private LlmGateway.ValidationResult validateReflectionDecision(ReflectionDecisionResponse response) {
        if (response == null) {
            return LlmGateway.ValidationResult.invalid("reflection decision must not be null");
        }
        if (response.shouldRefine() && isBlank(response.searchQuery())) {
            return LlmGateway.ValidationResult.invalid("search_query must not be blank when should_refine is true");
        }
        if (isBlank(response.reasoning())) {
            return LlmGateway.ValidationResult.invalid("reasoning must not be blank");
        }
        return LlmGateway.ValidationResult.valid();
    }

    private LlmGateway.ValidationResult validateReflectionSummary(ReflectionSummaryResponse response) {
        return validateText("updated_paragraph_latest_state", response == null ? null : response.updatedParagraphLatestState());
    }

    private LlmGateway.ValidationResult validateFinalReport(FinalReportResponse response) {
        return validateText("final_report", response == null ? null : response.finalReport());
    }

    private LlmGateway.ValidationResult validateText(String fieldName, String value) {
        return isBlank(value)
            ? LlmGateway.ValidationResult.invalid(fieldName + " must not be blank")
            : LlmGateway.ValidationResult.valid();
    }

    private ParagraphSummaryResponse defaultParagraphSummary(QueryNodeContext context, ParagraphState paragraph, String evidence) {
        return new ParagraphSummaryResponse("""
            ## %s

            围绕“%s”，本段重点是%s

            当前证据：
            %s

            默认降级总结：模型结构化输出失败，先保留可核验的搜索证据，等待后续补充。
            """.formatted(
            QueryPrompts.chapterHeading(context.getCurrentParagraphIndex(), paragraph.getTitle()),
            context.getRequest().query(),
            paragraph.getExpectedContent(),
            evidence
        ));
    }

    private ReflectionSummaryResponse defaultReflectionSummary(ParagraphState paragraph, String evidence) {
        return new ReflectionSummaryResponse(paragraph.getCurrentDraft() + """

            默认降级补充：
            - 新证据：%s
            - 处理说明：反思总结结构化输出失败，已将新增证据附加到当前段落。
            """.formatted(evidence));
    }

    private ReflectionDecisionResponse defaultReflectionDecision() {
        return new ReflectionDecisionResponse(false, "", "fallback-default-stop-reflection");
    }

    private FinalReportResponse defaultFinalReport(QueryNodeContext context) {
        String content = context.getState().getParagraphs().stream()
            .map(ParagraphState::getCurrentDraft)
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("未生成报告内容");
        return new FinalReportResponse(content);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ParagraphPlan(String title, @JsonProperty("content") String expectedContent) {
    }

    public record ParagraphSummaryResponse(@JsonProperty("paragraph_latest_state") String paragraphLatestState) {
    }

    public record ReflectionDecisionResponse(
        @JsonProperty("should_refine") boolean shouldRefine,
        @JsonProperty("search_query") String searchQuery,
        @JsonProperty("reasoning") String reasoning
    ) {
    }

    public record ReflectionSummaryResponse(
        @JsonProperty("updated_paragraph_latest_state") String updatedParagraphLatestState
    ) {
    }

    public record FinalReportResponse(@JsonProperty("final_report") String finalReport) {
    }
}
