package com.bettafish.common.api;

import java.util.List;
import java.util.Map;

public record EngineResult(
    EngineType engineType,
    String headline,
    String summary,
    List<String> keyPoints,
    List<SourceReference> sources,
    Map<String, String> metadata
) {
}
