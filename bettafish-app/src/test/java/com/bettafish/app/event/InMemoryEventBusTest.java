package com.bettafish.app.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.EngineStartedEvent;

class InMemoryEventBusTest {

    @Test
    void storesTaskScopedHistoryAndPushesToSubscribers() {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        List<AnalysisEvent> observed = new ArrayList<>();
        eventBus.subscribe(observed::add);

        EngineStartedEvent first = new EngineStartedEvent("task-1", "QUERY", Instant.parse("2026-03-18T00:00:00Z"));
        EngineStartedEvent second = new EngineStartedEvent("task-2", "MEDIA", Instant.parse("2026-03-18T00:00:01Z"));

        eventBus.publish(first);
        eventBus.publish(second);

        assertEquals(List.of(first, second), observed);
        assertEquals(List.of(first), eventBus.history("task-1"));
        assertEquals(List.of(second), eventBus.history("task-2"));
    }

    @Test
    void replaysTaskHistoryAndStopsAfterSubscriptionClosed() {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        List<AnalysisEvent> observed = new ArrayList<>();
        EngineStartedEvent replayed = new EngineStartedEvent("task-1", "QUERY", Instant.parse("2026-03-19T00:00:00Z"));
        EngineStartedEvent otherTaskReplay = new EngineStartedEvent("task-2", "MEDIA", Instant.parse("2026-03-19T00:00:01Z"));
        DeltaChunkEvent liveTaskEvent = new DeltaChunkEvent(
            "task-1",
            "QUERY",
            "summary",
            "chunk-1",
            1,
            Instant.parse("2026-03-19T00:00:02Z")
        );
        DeltaChunkEvent ignoredOtherTaskEvent = new DeltaChunkEvent(
            "task-2",
            "MEDIA",
            "summary",
            "chunk-2",
            1,
            Instant.parse("2026-03-19T00:00:03Z")
        );
        DeltaChunkEvent afterCloseEvent = new DeltaChunkEvent(
            "task-1",
            "QUERY",
            "summary",
            "chunk-3",
            2,
            Instant.parse("2026-03-19T00:00:04Z")
        );
        eventBus.publish(replayed);
        eventBus.publish(otherTaskReplay);

        EventBus.Subscription subscription = eventBus.subscribeTask("task-1", true, observed::add);
        eventBus.publish(liveTaskEvent);
        eventBus.publish(ignoredOtherTaskEvent);
        subscription.close();
        eventBus.publish(afterCloseEvent);

        assertEquals(List.of(replayed, liveTaskEvent), observed);
    }
}
