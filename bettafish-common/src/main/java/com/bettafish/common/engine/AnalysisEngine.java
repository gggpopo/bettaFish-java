package com.bettafish.common.engine;

import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;

@FunctionalInterface
public interface AnalysisEngine {

    EngineResult analyze(AnalysisRequest request);
}
