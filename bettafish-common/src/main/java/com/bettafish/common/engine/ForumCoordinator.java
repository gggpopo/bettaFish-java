package com.bettafish.common.engine;

import java.util.List;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.event.AnalysisEventPublisher;

@FunctionalInterface
public interface ForumCoordinator {

    ForumSummary coordinate(AnalysisRequest request, List<EngineResult> engineResults);

    default ForumSummary coordinate(AnalysisRequest request, List<EngineResult> engineResults,
                                    AnalysisEventPublisher publisher) {
        return coordinate(request, engineResults);
    }
}
