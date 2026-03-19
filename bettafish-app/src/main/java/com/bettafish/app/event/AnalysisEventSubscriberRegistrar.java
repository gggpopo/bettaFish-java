package com.bettafish.app.event;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import com.bettafish.common.event.AnalysisEventSubscriber;

@Component
public class AnalysisEventSubscriberRegistrar {

    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    public AnalysisEventSubscriberRegistrar(EventBus eventBus, List<AnalysisEventSubscriber> subscribers) {
        subscribers.forEach(subscriber -> subscriptions.add(eventBus.subscribe(subscriber)));
    }

    @PreDestroy
    void closeSubscriptions() {
        subscriptions.forEach(EventBus.Subscription::close);
        subscriptions.clear();
    }
}
