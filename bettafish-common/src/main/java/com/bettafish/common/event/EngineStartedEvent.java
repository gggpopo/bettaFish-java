package com.bettafish.common.event;

import java.time.Instant;

public record EngineStartedEvent(
    String taskId,
    String engineName,
    Instant occurredAt
) implements AnalysisEvent {
}
