package com.bettafish.common.event;

import java.time.Instant;

public record HostCommentEvent(
    String taskId,
    String hostName,
    String comment,
    Instant occurredAt
) implements AnalysisEvent {
}
