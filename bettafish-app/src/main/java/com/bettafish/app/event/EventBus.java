package com.bettafish.app.event;

import java.util.List;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.AnalysisEventSubscriber;

public interface EventBus extends AnalysisEventPublisher {

    Subscription subscribe(AnalysisEventSubscriber subscriber);

    Subscription subscribeTask(String taskId, boolean replayHistory, AnalysisEventSubscriber subscriber);

    List<AnalysisEvent> history(String taskId);

    @FunctionalInterface
    interface Subscription extends AutoCloseable {

        @Override
        void close();
    }
}
