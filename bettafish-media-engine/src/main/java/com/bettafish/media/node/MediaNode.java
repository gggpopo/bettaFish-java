package com.bettafish.media.node;

import com.bettafish.common.runtime.Node;
import com.bettafish.common.runtime.SingleParagraphWorkflowNodes;

public final class MediaNode {

    public static final Node<MediaNodeContext> PLAN_SEARCH =
        SingleParagraphWorkflowNodes.planSearch(context -> context.getAgent().planSearch(context));
    public static final Node<MediaNodeContext> EXECUTE_SEARCH =
        SingleParagraphWorkflowNodes.executeSearch(context -> context.getAgent().executeInitialSearch(context));
    public static final Node<MediaNodeContext> SUMMARIZE_FINDINGS =
        SingleParagraphWorkflowNodes.summarizeFindings(context -> context.getAgent().summarizeFindings(context));
    public static final Node<MediaNodeContext> REFLECT_ON_GAPS =
        SingleParagraphWorkflowNodes.reflectOnGaps(context -> context.getAgent().reflectOnGaps(context));
    public static final Node<MediaNodeContext> REFINE_SEARCH =
        SingleParagraphWorkflowNodes.refineSearch(context -> context.getAgent().executeRefinementSearch(context));
    public static final Node<MediaNodeContext> REFLECTION_SUMMARY =
        SingleParagraphWorkflowNodes.reflectionSummary(context -> context.getAgent().summarizeReflection(context));
    public static final Node<MediaNodeContext> FINALIZE_REPORT =
        SingleParagraphWorkflowNodes.finalizeReport(context -> context.getAgent().finalizeReport(context));

    private MediaNode() {
    }
}
