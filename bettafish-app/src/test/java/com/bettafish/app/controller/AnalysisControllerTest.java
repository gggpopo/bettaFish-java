package com.bettafish.app.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.time.Instant;
import java.util.ArrayDeque;
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
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.bettafish.app.event.InMemoryEventBus;
import com.bettafish.app.service.AnalysisCoordinator;
import com.bettafish.app.service.InMemoryAnalysisTaskRepository;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.ToolCalledEvent;

class AnalysisControllerTest {

    @Test
    void createsAndReadsAnalysisTasksOverHttp() throws Exception {
        ManualExecutor manualExecutor = new ManualExecutor();
        TestScheduledExecutorService scheduler = new TestScheduledExecutorService();
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY),
                engine(EngineType.MEDIA),
                engine(EngineType.INSIGHT)
            ),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            manualExecutor,
            scheduler,
            Duration.ofMinutes(5),
            eventBus
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

        String responseBody = mockMvc.perform(post("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"分析武汉大学樱花季舆情热度"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String taskId = JsonTestSupport.readJsonPath(responseBody, "$.taskId");
        manualExecutor.runAll();

        mockMvc.perform(get("/api/analysis/{taskId}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.report.title").value("BettaFish analysis report"))
            .andExpect(jsonPath("$.report.documentIr.meta.title").value("BettaFish analysis report"))
            .andExpect(jsonPath("$.report.documentIr.blocks.length()").value(2));
    }

    @Test
    void streamsTaskEventsOverSse() throws Exception {
        ManualExecutor manualExecutor = new ManualExecutor();
        TestScheduledExecutorService scheduler = new TestScheduledExecutorService();
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(
                engine(EngineType.QUERY),
                engine(EngineType.MEDIA),
                engine(EngineType.INSIGHT)
            ),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            manualExecutor,
            scheduler,
            Duration.ofMinutes(5),
            eventBus
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

        String responseBody = mockMvc.perform(post("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"分析武汉大学樱花季舆情热度"}
                    """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String taskId = JsonTestSupport.readJsonPath(responseBody, "$.taskId");
        manualExecutor.runAll();

        MvcResult asyncResult = mockMvc.perform(get("/api/analysis/{taskId}/events", taskId))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:EngineStartedEvent")))
            .andExpect(content().string(containsString("event:AnalysisCompleteEvent")))
            .andExpect(content().string(containsString("\"taskId\":\"" + taskId + "\"")))
            .andExpect(content().string(containsString("\"engineName\":\"QUERY\"")));
    }

    @Test
    void streamsToolAndRealSourceDeltaEventsOverSse() throws Exception {
        ManualExecutor manualExecutor = new ManualExecutor();
        TestScheduledExecutorService scheduler = new TestScheduledExecutorService();
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(engineWithRealSourceDelta(EngineType.QUERY)),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            manualExecutor,
            scheduler,
            Duration.ofMinutes(5),
            eventBus
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

        String responseBody = mockMvc.perform(post("/api/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"分析武汉大学樱花季舆情热度"}
                    """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String taskId = JsonTestSupport.readJsonPath(responseBody, "$.taskId");
        manualExecutor.runAll();

        MvcResult asyncResult = mockMvc.perform(get("/api/analysis/{taskId}/events", taskId))
            .andExpect(request().asyncStarted())
            .andReturn();

        String sseBody = mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:ToolCalledEvent")))
            .andExpect(content().string(containsString("event:DeltaChunkEvent")))
            .andExpect(content().string(containsString("https://news.sina.com.cn/c/2026-03-18/doc-query-1.shtml")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertTrue(sseBody.indexOf("event:ToolCalledEvent") < sseBody.indexOf("\"channel\":\"search-sources\""));
    }

    @Test
    void returnsNotFoundWhenStreamingUnknownTask() throws Exception {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        AnalysisCoordinator coordinator = new AnalysisCoordinator(
            List.of(engine(EngineType.QUERY)),
            forumCoordinator(),
            reportGenerator(),
            new InMemoryAnalysisTaskRepository(),
            new ManualExecutor(),
            new TestScheduledExecutorService(),
            Duration.ofMinutes(5),
            eventBus
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

        mockMvc.perform(get("/api/analysis/{taskId}/events", "missing-task"))
            .andExpect(status().isNotFound());
    }

    @Test
    void cancelsTaskOverHttpAndSseReceivesTerminalEvent() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("controller-cancel-"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("controller-cancel-scheduler-"));
        try {
            InMemoryEventBus eventBus = new InMemoryEventBus();
            AnalysisCoordinator coordinator = new AnalysisCoordinator(
                List.of(new BlockingCancellableEngine(EngineType.QUERY)),
                forumCoordinator(),
                reportGenerator(),
                new InMemoryAnalysisTaskRepository(),
                executor,
                scheduler,
                Duration.ofMinutes(5),
                eventBus
            );
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController(coordinator, eventBus, new ObjectMapper())).build();

            String responseBody = mockMvc.perform(post("/api/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"query":"取消中的舆情任务"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

            String taskId = JsonTestSupport.readJsonPath(responseBody, "$.taskId");

            MvcResult asyncResult = mockMvc.perform(get("/api/analysis/{taskId}/events", taskId))
                .andExpect(request().asyncStarted())
                .andReturn();

            mockMvc.perform(post("/api/analysis/{taskId}/cancel", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));

            asyncResult.getAsyncResult(2_000);
            mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:AnalysisCancelledEvent")))
                .andExpect(content().string(containsString("\"status\":\"CANCELLED\"")));

            mockMvc.perform(get("/api/analysis/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        } finally {
            scheduler.shutdownNow();
            executor.shutdownNow();
        }
    }

    private static AnalysisEngine engine(EngineType engineType) {
        return new AnalysisEngine() {
            @Override
            public String engineName() {
                return engineType.name();
            }

            @Override
            public EngineResult analyze(com.bettafish.common.api.AnalysisRequest request) {
                return new EngineResult(
                    engineType,
                    engineType.name() + " headline",
                    engineType.name() + " summary",
                    List.of(engineType.name() + " point"),
                    List.of(),
                    java.util.Map.of()
                );
            }
        };
    }

    private static AnalysisEngine engineWithRealSourceDelta(EngineType engineType) {
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
                publisher.publish(new ToolCalledEvent(
                    request.taskId(),
                    engineType.name(),
                    "tavilySearch",
                    request.query(),
                    "unit-test",
                    now
                ));
                publisher.publish(new DeltaChunkEvent(
                    request.taskId(),
                    engineType.name(),
                    "search-sources",
                    """
                        [1] 武汉大学樱花季游客爆满
                        https://news.sina.com.cn/c/2026-03-18/doc-query-1.shtml
                        """,
                    1,
                    now
                ));
                return new EngineResult(
                    engineType,
                    engineType.name() + " headline",
                    engineType.name() + " summary",
                    List.of(engineType.name() + " point"),
                    List.of(new com.bettafish.common.api.SourceReference(
                        "武汉大学樱花季游客爆满",
                        "https://news.sina.com.cn/c/2026-03-18/doc-query-1.shtml",
                        "武汉大学樱花季热度继续攀升。"
                    )),
                    java.util.Map.of()
                );
            }
        };
    }

    private static ForumCoordinator forumCoordinator() {
        return (request, results) -> new ForumSummary(
            "Forum overview",
            List.of("Consensus"),
            List.of("Open question")
        );
    }

    private static ReportGenerator reportGenerator() {
        return (request, input) -> new ReportDocument(
            "BettaFish analysis report",
            "Report summary",
            new DocumentIr(
                new DocumentMeta(
                    "BettaFish analysis report",
                    "Report summary",
                    request.query(),
                    "default",
                    Instant.parse("2026-03-18T00:00:00Z")
                ),
                List.of(
                    new DocumentBlock.HeadingBlock(2, "Forum"),
                    new DocumentBlock.ParagraphBlock("Forum section")
                )
            ),
            "<html><body>stub</body></html>"
        );
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

        private final AtomicBoolean shutdown = new AtomicBoolean(false);

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return new CompletedScheduledFuture();
        }

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            return List.of();
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
            throw new IllegalStateException("expected cancellation");
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
