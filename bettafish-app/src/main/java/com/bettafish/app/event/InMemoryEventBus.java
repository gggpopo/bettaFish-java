package com.bettafish.app.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventSubscriber;

@Component
public class InMemoryEventBus implements EventBus {

    private final Map<String, List<AnalysisEvent>> historyByTaskId = new ConcurrentHashMap<>();
    private final List<AnalysisEventSubscriber> subscribers = new CopyOnWriteArrayList<>();

    @Override
    public synchronized void publish(AnalysisEvent event) {
        historyByTaskId.computeIfAbsent(event.taskId(), ignored -> new CopyOnWriteArrayList<>()).add(event);
        subscribers.forEach(subscriber -> subscriber.onEvent(event));
    }

    @Override
    public synchronized Subscription subscribe(AnalysisEventSubscriber subscriber) {
        subscribers.add(subscriber);
        return () -> unsubscribe(subscriber);
    }

    @Override
    public synchronized Subscription subscribeTask(String taskId, boolean replayHistory, AnalysisEventSubscriber subscriber) {
        AnalysisEventSubscriber filteredSubscriber = event -> {
            if (taskId.equals(event.taskId())) {
                subscriber.onEvent(event);
            }
        };
        subscribers.add(filteredSubscriber);
        if (replayHistory) {
            historyByTaskId.getOrDefault(taskId, List.of()).forEach(subscriber::onEvent);
        }
        return () -> unsubscribe(filteredSubscriber);
    }

    @Override
    public synchronized List<AnalysisEvent> history(String taskId) {
        return List.copyOf(historyByTaskId.getOrDefault(taskId, List.of()));
    }

    private synchronized void unsubscribe(AnalysisEventSubscriber subscriber) {
        subscribers.remove(subscriber);
    }
}
