package com.bettafish.common.event;

import java.time.Instant;

public record AgentSpeechEvent(
    String taskId,
    String agentName,
    String content,
    Instant occurredAt
) {
}
