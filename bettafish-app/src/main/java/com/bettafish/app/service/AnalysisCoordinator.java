package com.bettafish.app.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import com.bettafish.app.event.EventBus;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.AnalysisTaskSnapshot;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ExecutionCancelledException;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.common.event.AnalysisCancelledEvent;
import com.bettafish.common.event.AnalysisCompleteEvent;
import com.bettafish.common.event.AnalysisFailedEvent;
import com.bettafish.common.event.AnalysisTimedOutEvent;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.EngineStartedEvent;
import org.springframework.stereotype.Service;

@Service
public class AnalysisCoordinator {

    private final List<AnalysisEngine> analysisEngines;
    private final ForumCoordinator forumCoordinator;
    private final ReportGenerator reportGenerator;
    private final AnalysisTaskRepository taskRepository;
    private final Executor analysisExecutor;
    private final ScheduledExecutorService analysisTimeoutScheduler;
    private final Duration taskTimeout;
    private final EventBus eventBus;
    private final ConcurrentHashMap<String, RunningTask> runningTasks = new ConcurrentHashMap<>();

    public AnalysisCoordinator(
        List<AnalysisEngine> analysisEngines,
        ForumCoordinator forumCoordinator,
        ReportGenerator reportGenerator,
        AnalysisTaskRepository taskRepository,
        Executor analysisExecutor,
        ScheduledExecutorService analysisTimeoutScheduler,
        Duration taskTimeout,
        EventBus eventBus
    ) {
        this.analysisEngines = analysisEngines;
        this.forumCoordinator = forumCoordinator;
        this.reportGenerator = reportGenerator;
        this.taskRepository = taskRepository;
        this.analysisExecutor = analysisExecutor;
        this.analysisTimeoutScheduler = analysisTimeoutScheduler;
        this.taskTimeout = taskTimeout == null ? Duration.ofMinutes(5) : taskTimeout;
        this.eventBus = eventBus;
    }

    public AnalysisTaskSnapshot startAnalysis(String query) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        AnalysisRequest request = new AnalysisRequest(taskId, query, now);
        AnalysisTaskSnapshot runningSnapshot = taskRepository.save(new AnalysisTaskSnapshot(
            taskId,
            query,
            AnalysisStatus.RUNNING,
            now,
            null,
            List.of(),
            null,
            null,
            null
        ));
        RunningTask runningTask = new RunningTask(request, runningSnapshot, new ExecutionContext(taskTimeout));
        runningTasks.put(taskId, runningTask);
        runningTask.timeoutFuture = analysisTimeoutScheduler.schedule(
            () -> handleTimeout(runningTask),
            taskTimeout.toMillis(),
            java.util.concurrent.TimeUnit.MILLISECONDS
        );
        runningTask.future = CompletableFuture.runAsync(() -> executeTask(runningTask), analysisExecutor);
        return runningSnapshot;
    }

    public Optional<AnalysisTaskSnapshot> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    public Optional<AnalysisTaskSnapshot> cancelAnalysis(String taskId) {
        Optional<AnalysisTaskSnapshot> snapshot = taskRepository.findById(taskId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        RunningTask runningTask = runningTasks.get(taskId);
        if (runningTask == null) {
            return snapshot;
        }
        runningTask.executionContext.cancel();
        return taskRepository.findById(taskId);
    }

    private void executeTask(RunningTask runningTask) {
        AnalysisRequest request = runningTask.request;
        try {
            runningTask.executionContext.throwIfCancellationRequested();
            List<EngineResult> engineResults = analysisEngines.stream()
                .map(engine -> {
                    runningTask.executionContext.throwIfCancellationRequested();
                    return analyzeEngine(request, engine, runningTask.executionContext);
                })
                .sorted(Comparator.comparing(EngineResult::engineType))
                .toList();
            runningTask.executionContext.throwIfCancellationRequested();
            var forumSummary = forumCoordinator.coordinate(request, engineResults, eventBus);
            runningTask.executionContext.throwIfCancellationRequested();
            ReportDocument report = reportGenerator.generate(
                request,
                new ReportInput(request.query(), engineResults, forumSummary),
                eventBus
            );
            eventBus.publish(new DeltaChunkEvent(request.taskId(), "REPORT", "report-summary", report.summary(), 1, Instant.now()));
            completeTask(runningTask, engineResults, forumSummary, report);
        } catch (ExecutionCancelledException ex) {
            if (ex.terminalStatus() == AnalysisStatus.TIMED_OUT) {
                timeOutTask(runningTask);
            } else {
                cancelTask(runningTask);
            }
        } catch (RuntimeException ex) {
            failTask(runningTask, ex);
        }
    }

    private EngineResult analyzeEngine(AnalysisRequest request, AnalysisEngine engine, ExecutionContext executionContext) {
        executionContext.throwIfCancellationRequested();
        eventBus.publish(new EngineStartedEvent(request.taskId(), engine.engineName(), Instant.now()));
        EngineResult result = engine.analyze(request, eventBus, executionContext);
        eventBus.publish(new AgentSpeechEvent(
            request.taskId(),
            engine.engineName(),
            result.headline() + " | " + result.summary(),
            Instant.now()
        ));
        return result;
    }

    private void handleTimeout(RunningTask runningTask) {
        if (runningTask.executionContext.timeout()) {
            CompletableFuture<?> future = runningTask.future;
            if (future != null && future.isDone()) {
                return;
            }
        }
    }

    private AnalysisTaskSnapshot completeTask(RunningTask runningTask,
                                              List<EngineResult> engineResults,
                                              com.bettafish.common.api.ForumSummary forumSummary,
                                              ReportDocument report) {
        return finalizeTask(
            runningTask,
            new AnalysisTaskSnapshot(
                runningTask.request.taskId(),
                runningTask.request.query(),
                AnalysisStatus.COMPLETED,
                runningTask.initialSnapshot.createdAt(),
                Instant.now(),
                engineResults,
                forumSummary,
                report,
                null
            ),
            snapshot -> new AnalysisCompleteEvent(snapshot.taskId(), snapshot, Instant.now())
        );
    }

    private AnalysisTaskSnapshot cancelTask(RunningTask runningTask) {
        return finalizeTask(
            runningTask,
            new AnalysisTaskSnapshot(
                runningTask.request.taskId(),
                runningTask.request.query(),
                AnalysisStatus.CANCELLED,
                runningTask.initialSnapshot.createdAt(),
                Instant.now(),
                List.of(),
                null,
                null,
                runningTask.executionContext.terminalMessage()
            ),
            snapshot -> new AnalysisCancelledEvent(snapshot.taskId(), snapshot, Instant.now())
        );
    }

    private AnalysisTaskSnapshot timeOutTask(RunningTask runningTask) {
        return finalizeTask(
            runningTask,
            new AnalysisTaskSnapshot(
                runningTask.request.taskId(),
                runningTask.request.query(),
                AnalysisStatus.TIMED_OUT,
                runningTask.initialSnapshot.createdAt(),
                Instant.now(),
                List.of(),
                null,
                null,
                runningTask.executionContext.terminalMessage()
            ),
            snapshot -> new AnalysisTimedOutEvent(snapshot.taskId(), snapshot, Instant.now())
        );
    }

    private AnalysisTaskSnapshot failTask(RunningTask runningTask, RuntimeException ex) {
        return finalizeTask(
            runningTask,
            new AnalysisTaskSnapshot(
                runningTask.request.taskId(),
                runningTask.request.query(),
                AnalysisStatus.FAILED,
                runningTask.initialSnapshot.createdAt(),
                Instant.now(),
                List.of(),
                null,
                null,
                ex.getMessage()
            ),
            snapshot -> new AnalysisFailedEvent(snapshot.taskId(), snapshot, Instant.now())
        );
    }

    private AnalysisTaskSnapshot finalizeTask(RunningTask runningTask,
                                              AnalysisTaskSnapshot terminalSnapshot,
                                              java.util.function.Function<AnalysisTaskSnapshot, com.bettafish.common.event.AnalysisEvent> eventFactory) {
        if (!runningTask.terminalized.compareAndSet(false, true)) {
            return taskRepository.findById(runningTask.request.taskId()).orElse(terminalSnapshot);
        }
        ScheduledFuture<?> timeoutFuture = runningTask.timeoutFuture;
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }
        AnalysisTaskSnapshot saved = taskRepository.save(terminalSnapshot);
        runningTasks.remove(runningTask.request.taskId());
        eventBus.publish(eventFactory.apply(saved));
        return saved;
    }

    private static final class RunningTask {

        private final AnalysisRequest request;
        private final AnalysisTaskSnapshot initialSnapshot;
        private final ExecutionContext executionContext;
        private final AtomicBoolean terminalized = new AtomicBoolean(false);
        private volatile CompletableFuture<?> future;
        private volatile ScheduledFuture<?> timeoutFuture;

        private RunningTask(AnalysisRequest request, AnalysisTaskSnapshot initialSnapshot, ExecutionContext executionContext) {
            this.request = request;
            this.initialSnapshot = initialSnapshot;
            this.executionContext = executionContext;
        }
    }
}
