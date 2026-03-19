package com.bettafish.common.event;

import java.time.Instant;

public record ToolCalledEvent(
    String taskId,
    String engineName,
    String toolName,
    String toolInput,
    String reasoning,
    Instant occurredAt
) implements AnalysisEvent {
}
