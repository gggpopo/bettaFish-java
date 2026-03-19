package com.bettafish.common.event;

@FunctionalInterface
public interface AnalysisEventPublisher {

    AnalysisEventPublisher NOOP = event -> {
    };

    void publish(AnalysisEvent event);

    static AnalysisEventPublisher noop() {
        return NOOP;
    }
}
