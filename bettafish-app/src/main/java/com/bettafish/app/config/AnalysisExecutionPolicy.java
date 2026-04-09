package com.bettafish.app.config;

import java.time.Duration;

public record AnalysisExecutionPolicy(
    Duration taskTimeout,
    Duration engineTimeout,
    int maxConcurrentEngines
) {

    private static final Duration DEFAULT_TASK_TIMEOUT = Duration.ofMinutes(5);

    public AnalysisExecutionPolicy {
        taskTimeout = normalizeTaskTimeout(taskTimeout);
        engineTimeout = normalizeEngineTimeout(engineTimeout, taskTimeout);
        maxConcurrentEngines = Math.max(1, maxConcurrentEngines);
    }

    private static Duration normalizeTaskTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return DEFAULT_TASK_TIMEOUT;
        }
        return timeout;
    }

    private static Duration normalizeEngineTimeout(Duration timeout, Duration taskTimeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return taskTimeout;
        }
        return timeout.compareTo(taskTimeout) > 0 ? taskTimeout : timeout;
    }
}
