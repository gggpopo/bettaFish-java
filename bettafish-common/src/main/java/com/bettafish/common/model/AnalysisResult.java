package com.bettafish.common.model;

import java.util.List;
import com.bettafish.common.api.EngineResult;

public record AnalysisResult(
    String agentName,
    String headline,
    String summary,
    List<String> keyPoints
) {
    public static AnalysisResult fromEngineResult(EngineResult engineResult) {
        return new AnalysisResult(
            engineResult.engineType().name(),
            engineResult.headline(),
            engineResult.summary(),
            engineResult.keyPoints()
        );
    }
}
