package com.bettafish.app.controller;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bettafish.app.event.EventBus;
import com.bettafish.app.service.AnalysisCoordinator;
import com.bettafish.common.api.AnalysisTaskSnapshot;
import com.bettafish.common.event.AnalysisCancelledEvent;
import com.bettafish.common.event.AnalysisCompleteEvent;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisFailedEvent;
import com.bettafish.common.event.AnalysisTimedOutEvent;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisCoordinator analysisCoordinator;
    private final EventBus eventBus;
    private final ObjectMapper objectMapper;

    public AnalysisController(AnalysisCoordinator analysisCoordinator, EventBus eventBus, ObjectMapper objectMapper) {
        this.analysisCoordinator = analysisCoordinator;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @GetMapping
    public ResponseEntity<java.util.List<AnalysisTaskSnapshot>> listAnalyses() {
        return ResponseEntity.ok(analysisCoordinator.listTasks());
    }

    @PostMapping
    public ResponseEntity<AnalysisTaskSnapshot> createAnalysis(@RequestBody CreateAnalysisTaskRequest request) {
        return ResponseEntity.ok(analysisCoordinator.startAnalysis(request.query()));
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<AnalysisTaskSnapshot> cancelAnalysis(@PathVariable String taskId) {
        return analysisCoordinator.cancelAnalysis(taskId)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found: " + taskId));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<AnalysisTaskSnapshot> getAnalysis(@PathVariable String taskId) {
        return analysisCoordinator.getTask(taskId)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found: " + taskId));
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalysisEvents(@PathVariable String taskId) {
        analysisCoordinator.getTask(taskId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found: " + taskId));

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean streamClosed = new AtomicBoolean(false);
        AtomicReference<EventBus.Subscription> subscriptionRef = new AtomicReference<>();

        EventBus.Subscription subscription = eventBus.subscribeTask(taskId, true, event -> {
            if (streamClosed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                    .name(event.kind())
                    .data(objectMapper.writeValueAsString(event)));
                if (isTerminalEvent(event)) {
                    streamClosed.set(true);
                    emitter.complete();
                }
            } catch (IOException ex) {
                streamClosed.set(true);
                emitter.completeWithError(ex);
            }
        });

        subscriptionRef.set(subscription);
        if (streamClosed.get()) {
            subscription.close();
            return emitter;
        }

        emitter.onCompletion(() -> closeSubscription(subscriptionRef));
        emitter.onTimeout(() -> {
            streamClosed.set(true);
            closeSubscription(subscriptionRef);
            emitter.complete();
        });
        emitter.onError(ex -> {
            streamClosed.set(true);
            closeSubscription(subscriptionRef);
        });
        return emitter;
    }

    private boolean isTerminalEvent(AnalysisEvent event) {
        return event instanceof AnalysisCompleteEvent
            || event instanceof AnalysisFailedEvent
            || event instanceof AnalysisCancelledEvent
            || event instanceof AnalysisTimedOutEvent;
    }

    private void closeSubscription(AtomicReference<EventBus.Subscription> subscriptionRef) {
        EventBus.Subscription subscription = subscriptionRef.getAndSet(null);
        if (subscription != null) {
            subscription.close();
        }
    }
}
