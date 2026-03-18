package com.bettafish.common.api;

import java.time.Instant;

public record AnalysisRequest(
    String taskId,
    String query,
    Instant requestedAt
) {
}
