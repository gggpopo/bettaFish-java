package com.bettafish.common.api;

import java.time.Instant;
import java.util.List;

public record AnalysisTaskSnapshot(
    String taskId,
    String query,
    AnalysisStatus status,
    Instant createdAt,
    Instant completedAt,
    List<EngineResult> engineResults,
    ForumSummary forumSummary,
    ReportDocument report,
    String errorMessage
) {
}
