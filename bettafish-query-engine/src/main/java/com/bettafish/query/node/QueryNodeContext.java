package com.bettafish.query.node;

import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.ParagraphState;
import com.bettafish.common.runtime.NodeContext;
import com.bettafish.query.QueryAgent;

public class QueryNodeContext extends NodeContext<QueryNode> {

    private final QueryAgent agent;
    private final AnalysisRequest request;
    private final AgentState state;
    private final int maxReflections;
    private final int maxParagraphs;
    private int currentParagraphIndex = -1;
    private int pendingReflectionRound = -1;
    private String pendingSearchQuery = "";
    private String pendingSearchReasoning = "";

    public QueryNodeContext(QueryAgent agent, AnalysisRequest request, AgentState state,
                            int maxReflections, int maxParagraphs) {
        this(agent, request, state, maxReflections, maxParagraphs, AnalysisEventPublisher.noop());
    }

    public QueryNodeContext(QueryAgent agent, AnalysisRequest request, AgentState state,
                            int maxReflections, int maxParagraphs, AnalysisEventPublisher publisher) {
        super(request.taskId(), "QUERY", publisher);
        this.agent = agent;
        this.request = request;
        this.state = state;
        this.maxReflections = maxReflections;
        this.maxParagraphs = maxParagraphs;
    }

    public QueryAgent getAgent() {
        return agent;
    }

    public AnalysisRequest getRequest() {
        return request;
    }

    public AgentState getState() {
        return state;
    }

    public int getMaxReflections() {
        return maxReflections;
    }

    public int getMaxParagraphs() {
        return maxParagraphs;
    }

    public int getCurrentParagraphIndex() {
        return currentParagraphIndex;
    }

    public void setCurrentParagraphIndex(int currentParagraphIndex) {
        this.currentParagraphIndex = currentParagraphIndex;
    }

    public ParagraphState getCurrentParagraph() {
        return state.getParagraphs().get(currentParagraphIndex);
    }

    public int getPendingReflectionRound() {
        return pendingReflectionRound;
    }

    public void setPendingReflectionRound(int pendingReflectionRound) {
        this.pendingReflectionRound = pendingReflectionRound;
    }

    public String getPendingSearchQuery() {
        return pendingSearchQuery;
    }

    public void setPendingSearchQuery(String pendingSearchQuery) {
        this.pendingSearchQuery = pendingSearchQuery;
    }

    public String getPendingSearchReasoning() {
        return pendingSearchReasoning;
    }

    public void setPendingSearchReasoning(String pendingSearchReasoning) {
        this.pendingSearchReasoning = pendingSearchReasoning;
    }

    @Override
    protected void onEnterNode(QueryNode node) {
        super.onEnterNode(node);
        state.setCurrentNode(node.name());
        state.setStatus(switch (node) {
            case PLAN_SEARCH -> "PLANNING";
            case EXECUTE_SEARCH -> "SEARCHING";
            case SUMMARIZE_FINDINGS -> "SUMMARIZING";
            case REFLECT_ON_GAPS -> "REFLECTING";
            case REFINE_SEARCH -> "REFINING";
            case FINALIZE_REPORT -> "FINALIZING";
        });
    }
}
