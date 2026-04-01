package com.bettafish.insight.node;

import java.util.List;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.runtime.SingleParagraphNodeContext;
import com.bettafish.insight.InsightAgent;
import com.bettafish.insight.SentimentSignal;

public class InsightNodeContext extends SingleParagraphNodeContext {

    private final InsightAgent agent;
    private final SentimentSignal sentimentSignal;
    private List<String> latestKeywords = List.of();

    public InsightNodeContext(InsightAgent agent, AnalysisRequest request, AgentState state,
                              SentimentSignal sentimentSignal, int maxReflections,
                              AnalysisEventPublisher publisher) {
        super("INSIGHT", request, state, maxReflections, publisher);
        this.agent = agent;
        this.sentimentSignal = sentimentSignal;
    }

    public InsightAgent getAgent() {
        return agent;
    }

    public SentimentSignal getSentimentSignal() {
        return sentimentSignal;
    }

    public List<String> getLatestKeywords() {
        return latestKeywords;
    }

    public void setLatestKeywords(List<String> latestKeywords) {
        this.latestKeywords = latestKeywords == null ? List.of() : List.copyOf(latestKeywords);
    }
}
