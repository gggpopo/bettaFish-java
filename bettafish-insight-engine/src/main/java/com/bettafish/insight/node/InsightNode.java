package com.bettafish.insight.node;

import com.bettafish.common.runtime.Node;
import com.bettafish.common.runtime.SingleParagraphWorkflowNodes;

public final class InsightNode {

    public static final Node<InsightNodeContext> PLAN_SEARCH =
        SingleParagraphWorkflowNodes.planSearch(context -> context.getAgent().planSearch(context));
    public static final Node<InsightNodeContext> EXECUTE_SEARCH =
        SingleParagraphWorkflowNodes.executeSearch(context -> context.getAgent().executeInitialSearch(context));
    public static final Node<InsightNodeContext> SUMMARIZE_FINDINGS =
        SingleParagraphWorkflowNodes.summarizeFindings(context -> context.getAgent().summarizeFindings(context));
    public static final Node<InsightNodeContext> REFLECT_ON_GAPS =
        SingleParagraphWorkflowNodes.reflectOnGaps(context -> context.getAgent().reflectOnGaps(context));
    public static final Node<InsightNodeContext> REFINE_SEARCH =
        SingleParagraphWorkflowNodes.refineSearch(context -> context.getAgent().executeRefinementSearch(context));
    public static final Node<InsightNodeContext> REFLECTION_SUMMARY =
        SingleParagraphWorkflowNodes.reflectionSummary(context -> context.getAgent().summarizeReflection(context));
    public static final Node<InsightNodeContext> FINALIZE_REPORT =
        SingleParagraphWorkflowNodes.finalizeReport(context -> context.getAgent().finalizeReport(context));

    private InsightNode() {
    }
}
