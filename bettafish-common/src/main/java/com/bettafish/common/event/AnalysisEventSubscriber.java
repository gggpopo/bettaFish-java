package com.bettafish.common.event;

@FunctionalInterface
public interface AnalysisEventSubscriber {

    void onEvent(AnalysisEvent event);
}
