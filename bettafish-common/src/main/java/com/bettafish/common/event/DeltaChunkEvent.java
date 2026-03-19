package com.bettafish.common.event;

import java.time.Instant;

public record DeltaChunkEvent(
    String taskId,
    String engineName,
    String channel,
    String content,
    int sequence,
    Instant occurredAt
) implements AnalysisEvent {
}
