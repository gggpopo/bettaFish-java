package com.bettafish.query.node;

import com.bettafish.common.runtime.Node;

public final class QueryNode {

    public static final Node<QueryNodeContext> PLAN_PARAGRAPHS = new PlanParagraphsNode();
    public static final Node<QueryNodeContext> FIRST_SEARCH_DECISION = new FirstSearchDecisionNode();
    public static final Node<QueryNodeContext> TOOL_EXECUTE = new ToolExecuteNode();
    public static final Node<QueryNodeContext> FIRST_SUMMARY = new FirstSummaryNode();
    public static final Node<QueryNodeContext> REFLECTION_DECISION = new ReflectionDecisionNode();
    public static final Node<QueryNodeContext> REFLECTION_SUMMARY = new ReflectionSummaryNode();
    public static final Node<QueryNodeContext> FORMAT_REPORT = new FormatReportNode();

    private QueryNode() {
    }

    private static final class PlanParagraphsNode implements Node<QueryNodeContext> {

        @Override
        public Node<QueryNodeContext> execute(QueryNodeContext context) {
            return context.getAgent().planParagraphs(context);
        }
    }

    private static final class FirstSearchDecisionNode implements Node<QueryNodeContext> {

        @Override
        public Node<QueryNodeContext> execute(QueryNodeContext context) {
            return context.getAgent().decideFirstSearch(context);
        }
    }

    private static final class ToolExecuteNode implements Node<QueryNodeContext> {

        @Override
        public Node<QueryNodeContext> execute(QueryNodeContext context) {
            return context.getAgent().executeTool(context);
        }
    }

    private static final class FirstSummaryNode implements Node<QueryNodeContext> {

        @Override
        public Node<QueryNodeContext> execute(QueryNodeContext context) {
            return context.getAgent().summarizeFirstSearch(context);
        }
    }

    private static final class ReflectionDecisionNode implements Node<QueryNodeContext> {

        @Override
        public Node<QueryNodeContext> execute(QueryNodeContext context) {
            return context.getAgent().decideReflection(context);
        }
    }

    private static final class ReflectionSummaryNode implements Node<QueryNodeContext> {

        @Override
        public Node<QueryNodeContext> execute(QueryNodeContext context) {
            return context.getAgent().summarizeReflection(context);
        }
    }

    private static final class FormatReportNode implements Node<QueryNodeContext> {

        @Override
        public Node<QueryNodeContext> execute(QueryNodeContext context) {
            return context.getAgent().formatReport(context);
        }
    }
}
