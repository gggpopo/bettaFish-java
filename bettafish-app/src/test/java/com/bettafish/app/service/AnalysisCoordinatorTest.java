package com.bettafish.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import com.bettafish.app.event.InMemoryEventBus;
import com.bettafish.app.config.AnalysisExecutionPolicy;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.common.event.AnalysisCancelledEvent;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.AnalysisCompleteEvent;
import com.bettafish.common.event.AnalysisFailedEvent;
import com.bettafish.common.event.AnalysisTimedOutEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.EngineStartedEvent;
import com.bettafish.common.event.HostCommentEvent;
import com.bettafish.common.event.NodeStartedEvent;
import com.bettafish.common.event.ToolCalledEvent;

class AnalysisCoordinatorTest {

    @Test
    void startsAnalysisAsynchronouslyAndStoresCompletedSnapshot() {
        ManualExecutor manualExecutor = new ManualExecutor();
        TestScheduledExecutorService scheduler = new TestScheduledExecutorService();
        InMemoryAnalysisTaskRepository taskRepository = new InMemoryAnalysisTaskRepository();
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY, "Query summary"),
                engine(EngineType.MEDIA, "Media summary"),
                engine(EngineType.INSIGHT, "Insight summary")
            ),
            forumCoordinator(),
            reportGenerator(),
            taskRepository,
            manualExecutor,
            Runnable::run,
            scheduler,
            new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMinutes(5), 3),
            eventBus
        );

        var snapshot = coordinator.startAnalysis("分析武汉大学樱花季舆情热度");

        assertNotNull(snapshot.taskId());
        assertEquals(AnalysisStatus.RUNNING, snapshot.status());
        assertTrue(snapshot.completedAt() == null);
        assertTrue(eventBus.history(snapshot.taskId()).isEmpty());

        manualExecutor.runAll();

        var completed = coordinator.getTask(snapshot.taskId()).orElseThrow();
        List<?> events = eventBus.history(snapshot.taskId());

        assertEquals(AnalysisStatus.COMPLETED, completed.status());
        assertEquals(3, completed.engineResults().size());
        assertEquals("Forum overview", completed.forumSummary().overview());
        assertEquals(4, completed.report().sections().size());
        assertEquals(8, completed.report().documentIr().blocks().size());
        assertTrue(taskRepository.findById(snapshot.taskId()).isPresent());
        assertEquals(List.of("QUERY", "MEDIA", "INSIGHT"),
            events.stream()
                .filter(EngineStartedEvent.class::isInstance)
                .map(EngineStartedEvent.class::cast)
                .map(EngineStartedEvent::engineName)
                .toList());
        assertTrue(events.stream().anyMatch(NodeStartedEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(ToolCalledEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(AgentSpeechEvent.class::isInstance));
        assertTrue(events.stream()
            .filter(AgentSpeechEvent.class::isInstance)
            .map(AgentSpeechEvent.class::cast)
            .anyMatch(event -> event.content().contains("QUERY headline")));
        assertTrue(events.stream().anyMatch(HostCommentEvent.class::isInstance));
        assertTrue(events.stream().anyMatch(DeltaChunkEvent.class::isInstance));
        assertTrue(events.getLast() instanceof AnalysisCompleteEvent);
    }

    @Test
    void publishesFailureEventWhenAnalysisCrashes() {
        ManualExecutor manualExecutor = new ManualExecutor();
        TestScheduledExecutorService scheduler = new TestScheduledExecutorService();
        InMemoryAnalysisTaskRepository taskRepository = new InMemoryAnalysisTaskRepository();
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(request -> {
                throw new IllegalStateException("boom");
            }),
            forumCoordinator(),
            reportGenerator(),
            taskRepository,
            manualExecutor,
            Runnable::run,
            scheduler,
            new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMinutes(5), 1),
            eventBus
        );

        var snapshot = coordinator.startAnalysis("分析失败任务");
        assertEquals(AnalysisStatus.RUNNING, snapshot.status());
        manualExecutor.runAll();
        var failed = coordinator.getTask(snapshot.taskId()).orElseThrow();

        assertEquals(AnalysisStatus.FAILED, failed.status());
        assertTrue(eventBus.history(snapshot.taskId()).stream().anyMatch(AnalysisFailedEvent.class::isInstance));
    }

    @Test
    void failsFastWhenOneParallelEngineCrashes() throws Exception {
        ExecutorService coordinatorExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-failure-"));
        ExecutorService engineExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("analysis-failure-engine-"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("analysis-failure-scheduler-"));
        CountDownLatch release = new CountDownLatch(1);
        try {
            CoordinatedBlockingEngine slowEngine = new CoordinatedBlockingEngine(EngineType.MEDIA, release);
            InMemoryEventBus eventBus = new InMemoryEventBus();
            AnalysisCoordinator coordinator = new AnalysisCoordinator(
                List.of(failingEngine(EngineType.QUERY, "boom"), slowEngine),
                forumCoordinator(),
                reportGenerator(),
                new InMemoryAnalysisTaskRepository(),
                coordinatorExecutor,
                engineExecutor,
                scheduler,
                new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMinutes(1), 2),
                eventBus
            );

            var snapshot = coordinator.startAnalysis("并行失败任务");

            assertTrue(slowEngine.awaitStarted());
            assertTrue(awaitCondition(() ->
                coordinator.getTask(snapshot.taskId()).map(task -> task.status() == AnalysisStatus.FAILED).orElse(false)
            ));
            assertTrue(eventBus.history(snapshot.taskId()).stream().anyMatch(AnalysisFailedEvent.class::isInstance));
        } finally {
            release.countDown();
            scheduler.shutdownNow();
            engineExecutor.shutdownNow();
            coordinatorExecutor.shutdownNow();
        }
    }

    @Test
    void cancelsRunningTaskAndPublishesTerminalEvent() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-test-"));
        ExecutorService engineExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-test-engine-"));
        TestScheduledExecutorService scheduler = new TestScheduledExecutorService();
        try {
            BlockingCancellableEngine engine = new BlockingCancellableEngine(EngineType.QUERY);
            InMemoryAnalysisTaskRepository taskRepository = new InMemoryAnalysisTaskRepository();
            InMemoryEventBus eventBus = new InMemoryEventBus();
            AnalysisCoordinator coordinator = new AnalysisCoordinator(
                List.of(engine),
                forumCoordinator(),
                reportGenerator(),
                taskRepository,
                executor,
                engineExecutor,
                scheduler,
                new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMinutes(5), 1),
                eventBus
            );

            var snapshot = coordinator.startAnalysis("取消任务");
            assertEquals(AnalysisStatus.RUNNING, snapshot.status());
            assertTrue(engine.awaitStarted());
            assertTrue(coordinator.cancelAnalysis(snapshot.taskId()).isPresent());
            assertTrue(awaitCondition(() ->
                coordinator.getTask(snapshot.taskId()).map(task -> task.status() == AnalysisStatus.CANCELLED).orElse(false)
            ));

            var cancelled = coordinator.getTask(snapshot.taskId()).orElseThrow();
            assertEquals(AnalysisStatus.CANCELLED, cancelled.status());
            assertTrue(awaitCondition(() ->
                eventBus.history(snapshot.taskId()).stream().anyMatch(AnalysisCancelledEvent.class::isInstance)
            ));
        } finally {
            engineExecutor.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    void timesOutRunningTaskAndPublishesTerminalEvent() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-timeout-"));
        ExecutorService engineExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-timeout-engine-"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("analysis-timeout-scheduler-"));
        try {
            BlockingCancellableEngine engine = new BlockingCancellableEngine(EngineType.QUERY);
            InMemoryAnalysisTaskRepository taskRepository = new InMemoryAnalysisTaskRepository();
            InMemoryEventBus eventBus = new InMemoryEventBus();
            AnalysisCoordinator coordinator = new AnalysisCoordinator(
                List.of(engine),
                forumCoordinator(),
                reportGenerator(),
                taskRepository,
                executor,
                engineExecutor,
                scheduler,
                new AnalysisExecutionPolicy(Duration.ofMillis(50), Duration.ofMillis(50), 1),
                eventBus
            );

            var snapshot = coordinator.startAnalysis("超时任务");
            assertEquals(AnalysisStatus.RUNNING, snapshot.status());
            assertTrue(engine.awaitStarted());
            assertTrue(awaitCondition(() ->
                coordinator.getTask(snapshot.taskId()).map(task -> task.status() == AnalysisStatus.TIMED_OUT).orElse(false)
            ));

            var timedOut = coordinator.getTask(snapshot.taskId()).orElseThrow();
            assertEquals(AnalysisStatus.TIMED_OUT, timedOut.status());
            assertTrue(awaitCondition(() ->
                eventBus.history(snapshot.taskId()).stream().anyMatch(AnalysisTimedOutEvent.class::isInstance)
            ));
        } finally {
            scheduler.shutdownNow();
            engineExecutor.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    void executesMultipleEnginesInParallel() throws Exception {
        ExecutorService coordinatorExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-coordinator-"));
        ExecutorService engineExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("analysis-engine-"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("analysis-parallel-scheduler-"));
        try {
            CountDownLatch release = new CountDownLatch(1);
            CoordinatedBlockingEngine first = new CoordinatedBlockingEngine(EngineType.QUERY, release);
            CoordinatedBlockingEngine second = new CoordinatedBlockingEngine(EngineType.MEDIA, release);
            AnalysisCoordinator coordinator = new AnalysisCoordinator(
                List.of(first, second),
                forumCoordinator(),
                reportGenerator(),
                new InMemoryAnalysisTaskRepository(),
                coordinatorExecutor,
                engineExecutor,
                scheduler,
                new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMinutes(1), 2),
                new InMemoryEventBus()
            );

            var snapshot = coordinator.startAnalysis("并行执行任务");

            assertTrue(first.awaitStarted());
            assertTrue(second.awaitStarted());
            release.countDown();
            assertTrue(awaitCondition(() ->
                coordinator.getTask(snapshot.taskId()).map(task -> task.status() == AnalysisStatus.COMPLETED).orElse(false)
            ));
        } finally {
            scheduler.shutdownNow();
            engineExecutor.shutdownNow();
            coordinatorExecutor.shutdownNow();
        }
    }

    @Test
    void respectsConfiguredEngineBulkheadLimit() throws Exception {
        ExecutorService coordinatorExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-coordinator-"));
        ExecutorService engineExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("analysis-engine-"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("analysis-bulkhead-scheduler-"));
        try {
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger startedOrder = new AtomicInteger();
            SequencedBlockingEngine first = new SequencedBlockingEngine(EngineType.QUERY, release, startedOrder);
            SequencedBlockingEngine second = new SequencedBlockingEngine(EngineType.MEDIA, release, startedOrder);
            SequencedBlockingEngine third = new SequencedBlockingEngine(EngineType.INSIGHT, release, startedOrder);
            AnalysisCoordinator coordinator = new AnalysisCoordinator(
                List.of(first, second, third),
                forumCoordinator(),
                reportGenerator(),
                new InMemoryAnalysisTaskRepository(),
                coordinatorExecutor,
                engineExecutor,
                scheduler,
                new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMinutes(1), 2),
                new InMemoryEventBus()
            );

            var snapshot = coordinator.startAnalysis("bulkhead 限流任务");

            assertTrue(first.awaitStarted());
            assertTrue(second.awaitStarted());
            assertFalse(third.awaitStarted(200, TimeUnit.MILLISECONDS));

            release.countDown();
            assertTrue(third.awaitStarted(2, TimeUnit.SECONDS));
            assertTrue(awaitCondition(() ->
                coordinator.getTask(snapshot.taskId()).map(task -> task.status() == AnalysisStatus.COMPLETED).orElse(false)
            ));
        } finally {
            scheduler.shutdownNow();
            engineExecutor.shutdownNow();
            coordinatorExecutor.shutdownNow();
        }
    }

    @Test
    void completesWithPlaceholderResultWhenSingleEngineTimesOut() throws Exception {
        ExecutorService coordinatorExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-coordinator-"));
        ExecutorService engineExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("analysis-engine-"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("analysis-engine-timeout-scheduler-"));
        try {
            AnalysisCoordinator coordinator = new AnalysisCoordinator(
                List.of(
                    engine(EngineType.QUERY, "Query summary"),
                    new BlockingCancellableEngine(EngineType.MEDIA)
                ),
                forumCoordinator(),
                reportGenerator(),
                new InMemoryAnalysisTaskRepository(),
                coordinatorExecutor,
                engineExecutor,
                scheduler,
                new AnalysisExecutionPolicy(Duration.ofMinutes(5), Duration.ofMillis(50), 2),
                new InMemoryEventBus()
            );

            var snapshot = coordinator.startAnalysis("engine 超时降级任务");

            assertTrue(awaitCondition(() ->
                coordinator.getTask(snapshot.taskId()).map(task -> task.status() == AnalysisStatus.COMPLETED).orElse(false)
            ));

            var completed = coordinator.getTask(snapshot.taskId()).orElseThrow();
            assertEquals(2, completed.engineResults().size());
            EngineResult timedOutEngine = completed.engineResults().stream()
                .filter(result -> result.engineType() == EngineType.MEDIA)
                .findFirst()
                .orElseThrow();
            assertEquals("TIMED_OUT", timedOutEngine.metadata().get("status"));
            assertTrue(timedOutEngine.summary().contains("timed out"));
        } finally {
            scheduler.shutdownNow();
            engineExecutor.shutdownNow();
            coordinatorExecutor.shutdownNow();
        }
    }

    private static AnalysisEngine engine(EngineType engineType, String summary) {
        return new AnalysisEngine() {
            @Override
            public String engineName() {
                return engineType.name();
            }

            @Override
            public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
                return analyze(request, event -> {
                });
            }

            @Override
            public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request,
                                        com.bettafish.common.event.AnalysisEventPublisher publisher) {
                Instant now = Instant.parse("2026-03-18T00:00:00Z");
                publisher.publish(new NodeStartedEvent(request.taskId(), engineType.name(), "PLAN", now));
                publisher.publish(new ToolCalledEvent(request.taskId(), engineType.name(), "stub-tool", request.query(), "unit-test", now));
                publisher.publish(new DeltaChunkEvent(request.taskId(), engineType.name(), "summary", summary, 1, now));
                return new EngineResult(
                    engineType,
                    engineType.name() + " headline",
                    summary,
                    List.of("key point"),
                    List.of(),
                    java.util.Map.of()
                );
            }
        };
    }

    private static AnalysisEngine failingEngine(EngineType engineType, String message) {
        return new AnalysisEngine() {
            @Override
            public String engineName() {
                return engineType.name();
            }

            @Override
            public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
                throw new IllegalStateException(message);
            }
        };
    }

    private static boolean awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static ForumCoordinator forumCoordinator() {
        return new ForumCoordinator() {
            @Override
            public ForumSummary coordinate(com.bettafish.common.api.AnalysisRequest request, List<EngineResult> results) {
                return coordinate(request, results, event -> {
                });
            }

            @Override
            public ForumSummary coordinate(com.bettafish.common.api.AnalysisRequest request,
                                           List<EngineResult> results,
                                           com.bettafish.common.event.AnalysisEventPublisher publisher) {
                Instant now = Instant.parse("2026-03-18T00:00:01Z");
                publisher.publish(new AgentSpeechEvent(request.taskId(), "QUERY", "观点：Query summary", now));
                publisher.publish(new HostCommentEvent(request.taskId(), "ForumHost", "主持总结", now));
                return new ForumSummary(
                    "Forum overview",
                    List.of("Consensus"),
                    List.of("Open question")
                );
            }
        };
    }

    private static ReportGenerator reportGenerator() {
        return new ReportGenerator() {
            @Override
            public ReportDocument generate(com.bettafish.common.api.AnalysisRequest request, com.bettafish.common.api.ReportInput input) {
                return generate(request, input, event -> {
                });
            }

            @Override
            public ReportDocument generate(com.bettafish.common.api.AnalysisRequest request,
                                           com.bettafish.common.api.ReportInput input,
                                           com.bettafish.common.event.AnalysisEventPublisher publisher) {
                publisher.publish(new DeltaChunkEvent(
                    request.taskId(),
                    "REPORT",
                    "report",
                    "Report draft chunk",
                    1,
                    Instant.parse("2026-03-18T00:00:02Z")
                ));
                return new ReportDocument(
                    "Report title",
                    "Report summary",
                    new DocumentIr(
                        new DocumentMeta(
                            "Report title",
                            "Report summary",
                            request.query(),
                            "default",
                            Instant.parse("2026-03-18T00:00:02Z")
                        ),
                        List.of(
                            new DocumentBlock.HeadingBlock(2, "Query"),
                            new DocumentBlock.ParagraphBlock("Query section"),
                            new DocumentBlock.HeadingBlock(2, "Media"),
                            new DocumentBlock.ParagraphBlock("Media section"),
                            new DocumentBlock.HeadingBlock(2, "Insight"),
                            new DocumentBlock.ParagraphBlock("Insight section"),
                            new DocumentBlock.HeadingBlock(2, "Forum"),
                            new DocumentBlock.ParagraphBlock("Forum section")
                        )
                    ),
                    "<html><body>stub</body></html>"
                );
            }
        };
    }

    private static final class ManualExecutor implements Executor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }

    private static final class TestScheduledExecutorService extends AbstractExecutorService implements ScheduledExecutorService {

        private final Queue<Runnable> scheduledTasks = new ArrayDeque<>();
        private final AtomicBoolean shutdown = new AtomicBoolean(false);

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduledTasks.add(command);
            return new CompletedScheduledFuture();
        }

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            List<Runnable> remaining = List.copyOf(scheduledTasks);
            scheduledTasks.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return shutdown.get();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CompletedScheduledFuture implements ScheduledFuture<Object> {

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(java.util.concurrent.Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    private static final class BlockingCancellableEngine implements AnalysisEngine {

        private final EngineType engineType;
        private final CountDownLatch started = new CountDownLatch(1);

        private BlockingCancellableEngine(EngineType engineType) {
            this.engineType = engineType;
        }

        @Override
        public String engineName() {
            return engineType.name();
        }

        @Override
        public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
            throw new UnsupportedOperationException("ExecutionContext overload expected");
        }

        @Override
        public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request,
                                    com.bettafish.common.event.AnalysisEventPublisher publisher,
                                    ExecutionContext executionContext) {
            started.countDown();
            while (!executionContext.isCancellationRequested()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            executionContext.throwIfCancellationRequested();
            throw new IllegalStateException("expected cancellation before completion");
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }
    }

    private static final class CoordinatedBlockingEngine implements AnalysisEngine {

        private final EngineType engineType;
        private final CountDownLatch release;
        private final CountDownLatch started = new CountDownLatch(1);

        private CoordinatedBlockingEngine(EngineType engineType, CountDownLatch release) {
            this.engineType = engineType;
            this.release = release;
        }

        @Override
        public String engineName() {
            return engineType.name();
        }

        @Override
        public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
            throw new UnsupportedOperationException("ExecutionContext overload expected");
        }

        @Override
        public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request,
                                    com.bettafish.common.event.AnalysisEventPublisher publisher,
                                    ExecutionContext executionContext) {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", ex);
            }
            return new EngineResult(
                engineType,
                engineType.name() + " headline",
                engineType.name() + " summary",
                List.of("key point"),
                List.of(),
                Map.of()
            );
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(1, TimeUnit.SECONDS);
        }
    }

    private static final class SequencedBlockingEngine implements AnalysisEngine {

        private final EngineType engineType;
        private final CountDownLatch release;
        private final AtomicInteger startedOrder;
        private final CountDownLatch started = new CountDownLatch(1);

        private SequencedBlockingEngine(EngineType engineType, CountDownLatch release, AtomicInteger startedOrder) {
            this.engineType = engineType;
            this.release = release;
            this.startedOrder = startedOrder;
        }

        @Override
        public String engineName() {
            return engineType.name();
        }

        @Override
        public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
            throw new UnsupportedOperationException("ExecutionContext overload expected");
        }

        @Override
        public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request,
                                    com.bettafish.common.event.AnalysisEventPublisher publisher,
                                    ExecutionContext executionContext) {
            startedOrder.incrementAndGet();
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", ex);
            }
            return new EngineResult(
                engineType,
                engineType.name() + " headline",
                engineType.name() + " summary",
                List.of("key point"),
                List.of(),
                Map.of()
            );
        }

        private boolean awaitStarted() throws InterruptedException {
            return awaitStarted(1, TimeUnit.SECONDS);
        }

        private boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private int index;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + index++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
