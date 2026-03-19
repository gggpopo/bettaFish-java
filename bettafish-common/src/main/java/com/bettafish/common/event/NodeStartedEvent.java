package com.bettafish.common.event;

import java.time.Instant;

public record NodeStartedEvent(
    String taskId,
    String engineName,
    String nodeName,
    Instant occurredAt
) implements AnalysisEvent {
}
