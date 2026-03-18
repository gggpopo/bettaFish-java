package com.bettafish.common.api;

import java.util.List;

public record ReportInput(
    String query,
    List<EngineResult> engineResults,
    ForumSummary forumSummary
) {
}
