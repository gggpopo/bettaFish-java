package com.bettafish.common.runtime;

import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.ParagraphState;

public abstract class SingleParagraphNodeContext extends NodeContext {

    private final AnalysisRequest request;
    private final AgentState state;
    private final int maxReflections;
    private String pendingSearchQuery = "";
    private String pendingSearchReasoning = "";

    protected SingleParagraphNodeContext(String engineName, AnalysisRequest request, AgentState state,
                                         int maxReflections, AnalysisEventPublisher publisher) {
        super(request.taskId(), engineName, publisher);
        this.request = request;
        this.state = state;
        this.maxReflections = maxReflections;
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

    public ParagraphState getParagraph() {
        return state.getParagraphs().getFirst();
    }

    @Override
    protected void onEnterNode(Node<?> node) {
        super.onEnterNode(node);
        state.setCurrentNode(node.name());
        state.setStatus(workflowStatus(node.name()));
    }

    protected String workflowStatus(String nodeName) {
        return switch (nodeName) {
            case "PlanSearchNode" -> "PLANNING";
            case "ExecuteSearchNode", "RefineSearchNode" -> "SEARCHING";
            case "SummarizeFindingsNode", "ReflectionSummaryNode" -> "SUMMARIZING";
            case "ReflectOnGapsNode" -> "DECIDING";
            case "FinalizeReportNode" -> "FINALIZING";
            default -> "RUNNING";
        };
    }
}
