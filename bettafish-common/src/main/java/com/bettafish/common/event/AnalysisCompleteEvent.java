package com.bettafish.common.event;

import java.time.Instant;
import com.bettafish.common.api.AnalysisTaskSnapshot;

public record AnalysisCompleteEvent(
    String taskId,
    AnalysisTaskSnapshot snapshot,
    Instant occurredAt
) implements AnalysisEvent {
}
