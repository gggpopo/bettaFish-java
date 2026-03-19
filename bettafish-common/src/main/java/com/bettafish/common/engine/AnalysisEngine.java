package com.bettafish.common.engine;

import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.event.AnalysisEventPublisher;

@FunctionalInterface
public interface AnalysisEngine {

    EngineResult analyze(AnalysisRequest request);

    default EngineResult analyze(AnalysisRequest request, AnalysisEventPublisher publisher) {
        return analyze(request);
    }

    default String engineName() {
        return getClass().getSimpleName();
    }
}
