package com.bettafish.query.node;

import com.bettafish.common.runtime.Node;

public enum QueryNode implements Node<QueryNode, QueryNodeContext> {
    PLAN_SEARCH {
        @Override
        public QueryNode execute(QueryNodeContext context) {
            return context.getAgent().planParagraphs(context);
        }
    },
    EXECUTE_SEARCH {
        @Override
        public QueryNode execute(QueryNodeContext context) {
            return context.getAgent().executeSearch(context);
        }
    },
    SUMMARIZE_FINDINGS {
        @Override
        public QueryNode execute(QueryNodeContext context) {
            return context.getAgent().summarizeFindings(context);
        }
    },
    REFLECT_ON_GAPS {
        @Override
        public QueryNode execute(QueryNodeContext context) {
            return context.getAgent().reflectOnGaps(context);
        }
    },
    REFINE_SEARCH {
        @Override
        public QueryNode execute(QueryNodeContext context) {
            return context.getAgent().refineSearch(context);
        }
    },
    FINALIZE_REPORT {
        @Override
        public QueryNode execute(QueryNodeContext context) {
            return context.getAgent().finalizeReport(context);
        }
    }
}
