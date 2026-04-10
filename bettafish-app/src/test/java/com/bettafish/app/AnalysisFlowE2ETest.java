package com.bettafish.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.bettafish.app.event.InMemoryEventBus;
import com.bettafish.app.config.AnalysisExecutionPolicy;
import com.bettafish.app.service.AnalysisCoordinator;
import com.bettafish.app.service.AnalysisTaskRepository;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.AnalysisTaskSnapshot;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.common.event.AnalysisCompleteEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.EngineStartedEvent;

class AnalysisFlowE2ETest {

    @Test
    void fullAnalysisFlow_fromRequestToReport() {
        ManualExecutor executor = new ManualExecutor();
        StubScheduler scheduler = new StubScheduler();
        InMemoryTaskRepository taskRepo = new InMemoryTaskRepository();
        InMemoryEventBus eventBus = new InMemoryEventBus();

        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(stubEngine(EngineType.QUERY), stubEngine(EngineType.MEDIA), stubEngine(EngineType.INSIGHT)),
            stubForumCoordinator(),
            stubReportGenerator(),
            taskRepo,
            executor,
            Runnable::run,
            scheduler,
            new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMinutes(5), 3),
            eventBus
        );

        var snapshot = coordinator.startAnalysis("E2E 全流程测试");
        assertThat(snapshot.status()).isEqualTo(AnalysisStatus.RUNNING);

        executor.runAll();

        var completed = coordinator.getTask(snapshot.taskId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.engineResults()).hasSize(3);
        assertThat(completed.forumSummary()).isNotNull();
        assertThat(completed.forumSummary().overview()).isEqualTo("E2E Forum overview");
        assertThat(completed.report()).isNotNull();
        assertThat(completed.report().html()).contains("<html>");
        assertThat(completed.report().documentIr().blocks()).isNotEmpty();

        List<?> events = eventBus.history(snapshot.taskId());
        assertThat(events.stream().filter(EngineStartedEvent.class::isInstance).count()).isEqualTo(3);
        assertThat(events.getLast()).isInstanceOf(AnalysisCompleteEvent.class);
    }

    private static AnalysisEngine stubEngine(EngineType type) {
        return new AnalysisEngine() {
            @Override
            public String engineName() { return type.name(); }

            @Override
            public EngineResult analyze(AnalysisRequest request) {
                return analyze(request, event -> {});
            }

            @Override
            public EngineResult analyze(AnalysisRequest request, AnalysisEventPublisher publisher) {
                publisher.publish(new EngineStartedEvent(request.taskId(), type.name(), Instant.now()));
                publisher.publish(new DeltaChunkEvent(request.taskId(), type.name(), "summary", type.name() + " result", 1, Instant.now()));
                return new EngineResult(type, type.name() + " headline", type.name() + " summary",
                    List.of("point"), List.of(), Map.of());
            }
        };
    }

    private static ForumCoordinator stubForumCoordinator() {
        return new ForumCoordinator() {
            @Override
            public ForumSummary coordinate(AnalysisRequest request, List<EngineResult> results) {
                return coordinate(request, results, event -> {});
            }
            @Override
            public ForumSummary coordinate(AnalysisRequest request, List<EngineResult> results, AnalysisEventPublisher publisher) {
                return new ForumSummary("E2E Forum overview", List.of("Consensus"), List.of("Open"));
            }
        };
    }

    private static ReportGenerator stubReportGenerator() {
        return new ReportGenerator() {
            @Override
            public ReportDocument generate(AnalysisRequest request, ReportInput input) {
                return generate(request, input, event -> {});
            }
            @Override
            public ReportDocument generate(AnalysisRequest request, ReportInput input, AnalysisEventPublisher publisher) {
                return new ReportDocument("E2E Report", "E2E Summary",
                    new DocumentIr(
                        new DocumentMeta("E2E Report", "E2E Summary", request.query(), "default", Instant.now()),
                        List.of(
                            new DocumentBlock.HeadingBlock(2, "Query"),
                            new DocumentBlock.ParagraphBlock("Query section"),
                            new DocumentBlock.HeadingBlock(2, "Media"),
                            new DocumentBlock.ParagraphBlock("Media section")
                        )
                    ),
                    "<html><body>E2E report</body></html>"
                );
            }
        };
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }

    private static final class InMemoryTaskRepository implements AnalysisTaskRepository {
        private final java.util.concurrent.ConcurrentHashMap<String, AnalysisTaskSnapshot> store = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public AnalysisTaskSnapshot save(AnalysisTaskSnapshot snapshot) { store.put(snapshot.taskId(), snapshot); return snapshot; }
        @Override public java.util.Optional<AnalysisTaskSnapshot> findById(String taskId) { return java.util.Optional.ofNullable(store.get(taskId)); }
        @Override public java.util.List<AnalysisTaskSnapshot> findAll() { return new java.util.ArrayList<>(store.values()); }
    }

    private static final class StubScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final AtomicBoolean shutdown = new AtomicBoolean(false);
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) { return new StubFuture(); }
        @Override public void shutdown() { shutdown.set(true); }
        @Override public List<Runnable> shutdownNow() { shutdown.set(true); return List.of(); }
        @Override public boolean isShutdown() { return shutdown.get(); }
        @Override public boolean isTerminated() { return shutdown.get(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
        @Override public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> c, long d, TimeUnit u) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable c, long i, long p, TimeUnit u) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable c, long i, long d, TimeUnit u) { throw new UnsupportedOperationException(); }
    }

    private static final class StubFuture implements ScheduledFuture<Object> {
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(java.util.concurrent.Delayed o) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
    }
}
