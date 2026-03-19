package com.bettafish.common.event;

import java.time.Instant;

public interface AnalysisEvent {

    String taskId();

    Instant occurredAt();

    default String kind() {
        return getClass().getSimpleName();
    }
}
