package com.bettafish.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
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
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import com.bettafish.app.event.InMemoryEventBus;
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
            scheduler,
            Duration.ofMinutes(5),
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
            scheduler,
            Duration.ofMinutes(5),
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
    void cancelsRunningTaskAndPublishesTerminalEvent() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-test-"));
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
                scheduler,
                Duration.ofMinutes(5),
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
            assertTrue(eventBus.history(snapshot.taskId()).stream().anyMatch(AnalysisCancelledEvent.class::isInstance));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timesOutRunningTaskAndPublishesTerminalEvent() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("analysis-timeout-"));
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
                scheduler,
                Duration.ofMillis(50),
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
            assertTrue(eventBus.history(snapshot.taskId()).stream().anyMatch(AnalysisTimedOutEvent.class::isInstance));
        } finally {
            scheduler.shutdownNow();
            executor.shutdownNow();
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
