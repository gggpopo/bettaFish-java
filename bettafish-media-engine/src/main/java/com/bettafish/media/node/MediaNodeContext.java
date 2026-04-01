package com.bettafish.media.node;

import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.runtime.SingleParagraphNodeContext;
import com.bettafish.media.MediaAgent;

public class MediaNodeContext extends SingleParagraphNodeContext {

    private final MediaAgent agent;

    public MediaNodeContext(MediaAgent agent, AnalysisRequest request, AgentState state, int maxReflections,
                            AnalysisEventPublisher publisher) {
        super("MEDIA", request, state, maxReflections, publisher);
        this.agent = agent;
    }

    public MediaAgent getAgent() {
        return agent;
    }
}
