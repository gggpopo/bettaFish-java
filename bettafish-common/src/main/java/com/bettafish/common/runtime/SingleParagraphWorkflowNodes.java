package com.bettafish.common.runtime;

import java.util.function.Function;

public final class SingleParagraphWorkflowNodes {

    private SingleParagraphWorkflowNodes() {
    }

    public static <C extends NodeContext> Node<C> planSearch(Function<C, Node<C>> transition) {
        return node("PlanSearchNode", transition);
    }

    public static <C extends NodeContext> Node<C> executeSearch(Function<C, Node<C>> transition) {
        return node("ExecuteSearchNode", transition);
    }

    public static <C extends NodeContext> Node<C> summarizeFindings(Function<C, Node<C>> transition) {
        return node("SummarizeFindingsNode", transition);
    }

    public static <C extends NodeContext> Node<C> reflectOnGaps(Function<C, Node<C>> transition) {
        return node("ReflectOnGapsNode", transition);
    }

    public static <C extends NodeContext> Node<C> refineSearch(Function<C, Node<C>> transition) {
        return node("RefineSearchNode", transition);
    }

    public static <C extends NodeContext> Node<C> reflectionSummary(Function<C, Node<C>> transition) {
        return node("ReflectionSummaryNode", transition);
    }

    public static <C extends NodeContext> Node<C> finalizeReport(Function<C, Node<C>> transition) {
        return node("FinalizeReportNode", transition);
    }

    private static <C extends NodeContext> Node<C> node(String name, Function<C, Node<C>> transition) {
        return new NamedNode<>(name, transition);
    }

    private record NamedNode<C extends NodeContext>(String name, Function<C, Node<C>> transition) implements Node<C> {

        @Override
        public Node<C> execute(C context) {
            return transition.apply(context);
        }
    }
}
