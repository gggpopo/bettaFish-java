package com.bettafish.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.NodeStartedEvent;
import com.bettafish.common.event.ToolCalledEvent;
import com.bettafish.common.engine.ForumGuidanceProvider;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.common.model.AgentState;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;
import com.bettafish.query.tool.TavilySearchTool;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class QueryAgentTest {

    @Test
    void runsFullInMemoryWorkflowAndPersistsIntermediateState() throws Exception {
        RecordingLlmGateway llmGateway = new RecordingLlmGateway();
        StaticForumGuidanceProvider guidanceProvider = new StaticForumGuidanceProvider();
        HttpServer tavilyServer = startServer();
        QueryAgent agent = new QueryAgent(
            new TavilySearchTool(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://localhost:" + tavilyServer.getAddress().getPort(),
                "tvly-test-key",
                3
            ),
            llmGateway,
            guidanceProvider,
            2,
            3
        );
        RecordingPublisher publisher = new RecordingPublisher();
        AnalysisRequest request = new AnalysisRequest(
            "task-1",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        );

        AgentState state;
        try {
            state = agent.runWorkflow(request, publisher);
        } finally {
            tavilyServer.stop(0);
        }
        tavilyServer = startServer();
        agent = new QueryAgent(
            new TavilySearchTool(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://localhost:" + tavilyServer.getAddress().getPort(),
                "tvly-test-key",
                3
            ),
            llmGateway,
            guidanceProvider,
            2,
            3
        );
        var result = agent.analyze(request, publisher);
        tavilyServer.stop(0);

        assertEquals("QUERY", state.getAgentName());
        assertEquals("COMPLETED", state.getStatus());
        assertEquals(2, state.getRound());
        assertEquals(3, state.getParagraphs().size());
        assertFalse(state.getFinalReport().isBlank());
        assertTrue(state.getFinalReport().contains("终稿"));
        assertEquals(List.of("结构规划1", "结构规划2", "结构规划3"),
            state.getParagraphs().stream().map(paragraph -> paragraph.getTitle()).toList());

        state.getParagraphs().forEach(paragraph -> {
            assertTrue(paragraph.isCompleted());
            assertFalse(paragraph.getCurrentDraft().isBlank());
            assertEquals(2, paragraph.getReflectionRoundsCompleted());
            assertEquals(3, paragraph.getSearchHistory().size());
            assertFalse(paragraph.getSearchHistory().getFirst().getSources().isEmpty());
        });

        assertEquals(EngineType.QUERY, result.engineType());
        assertEquals("state-machine", result.metadata().get("mode"));
        assertEquals("3", result.metadata().get("paragraphCount"));
        assertEquals("2", result.metadata().get("reflectionCount"));
        assertTrue(result.summary().contains("终稿"));
        assertTrue(result.sources().size() >= 3);
        assertTrue(result.sources().stream().noneMatch(source -> source.url().contains("example.com")));
        assertTrue(llmGateway.calls().stream().allMatch(call -> call.clientName().equals("queryChatClient")));
        assertTrue(llmGateway.calls().size() >= 10);
        assertTrue(publisher.events().stream().anyMatch(NodeStartedEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(ToolCalledEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(DeltaChunkEvent.class::isInstance));
        assertTrue(publisher.events().stream().anyMatch(AgentSpeechEvent.class::isInstance));
        assertEquals("task-1", publisher.events().getFirst().taskId());
        assertTrue(publisher.events().stream()
            .filter(NodeStartedEvent.class::isInstance)
            .map(NodeStartedEvent.class::cast)
            .map(NodeStartedEvent::nodeName)
            .toList()
            .containsAll(List.of(
                "PlanParagraphsNode",
                "FirstSearchDecisionNode",
                "ToolExecuteNode",
                "FirstSummaryNode",
                "ReflectionDecisionNode",
                "ReflectionSummaryNode",
                "FormatReportNode"
            )));
        assertEquals("FormatReportNode", state.getCurrentNode());
        assertTrue(llmGateway.calls().stream()
            .map(Call::userPrompt)
            .anyMatch(prompt -> prompt.contains("请优先补齐证据缺口并追问争议来源")));
        assertEquals(1, state.getForumGuidanceHistory().size());
        assertEquals(1, state.getParagraphs().getFirst().getForumGuidanceRevisionApplied());
        assertTrue(state.getForumMessages().stream().anyMatch(message -> message.content().contains("请优先补齐证据缺口")));
        assertTrue(state.getParagraphs().stream()
            .flatMap(paragraph -> paragraph.getSearchHistory().stream())
            .flatMap(searchRecord -> searchRecord.getSources().stream())
            .noneMatch(source -> source.url().contains("example.com")));
        int toolEventIndex = indexOf(publisher.events(), ToolCalledEvent.class);
        int sourceDeltaIndex = indexOfSourceDelta(publisher.events(), toolEventIndex);
        assertTrue(sourceDeltaIndex > toolEventIndex);
        DeltaChunkEvent sourceDelta = (DeltaChunkEvent) publisher.events().get(sourceDeltaIndex);
        assertEquals("search-sources", sourceDelta.channel());
        assertTrue(sourceDelta.content().contains("https://news.sina.com.cn/"));
        assertTrue(sourceDelta.content().contains("https://www.thepaper.cn/"));
    }

    @Test
    void downgradesToFallbackDefaultsWhenGatewayCannotReturnJson() {
        FallbackOnlyLlmGateway llmGateway = new FallbackOnlyLlmGateway();
        QueryAgent agent = new QueryAgent(
            new TavilySearchTool(HttpClient.newHttpClient(), new ObjectMapper(), "http://localhost:65535", "", 3),
            llmGateway,
            2,
            3
        );

        AgentState state = agent.runWorkflow(new AnalysisRequest(
            "task-2",
            "武汉大学樱花季舆情热度",
            Instant.parse("2026-03-18T00:00:00Z")
        ));

        assertEquals("COMPLETED", state.getStatus());
        assertEquals(3, state.getParagraphs().size());
        assertTrue(state.getParagraphs().stream().allMatch(paragraph -> paragraph.isCompleted()));
        assertFalse(state.getFinalReport().isBlank());
        assertTrue(llmGateway.fallbackCount() > 0);
    }

    private static final class RecordingLlmGateway implements LlmGateway {

        private final List<Call> calls = new ArrayList<>();
        private int summaryCalls;
        private final Map<String, Integer> reflectionDecisionCallsByParagraph = new HashMap<>();

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType) {
            calls.add(new Call(clientName, systemPrompt, userPrompt, responseType.getSimpleName()));

            if (responseType.equals(QueryAgent.ParagraphSummaryResponse.class)) {
                summaryCalls++;
                return responseType.cast(new QueryAgent.ParagraphSummaryResponse("段落总结-" + summaryCalls));
            }
            if (responseType.equals(QueryAgent.ReflectionSummaryResponse.class)) {
                summaryCalls++;
                return responseType.cast(new QueryAgent.ReflectionSummaryResponse("反思扩写-" + summaryCalls));
            }
            if (responseType.equals(QueryAgent.ReflectionDecisionResponse.class)) {
                String paragraphKey = extractParagraphKey(userPrompt);
                int count = reflectionDecisionCallsByParagraph.getOrDefault(paragraphKey, 0);
                reflectionDecisionCallsByParagraph.put(paragraphKey, count + 1);
                if (count >= 2) {
                    return responseType.cast(new QueryAgent.ReflectionDecisionResponse(false, "", "enough"));
                }
                return responseType.cast(new QueryAgent.ReflectionDecisionResponse(
                    true,
                    "补充检索-" + paragraphKey + "-" + (count + 1),
                    "gap-" + paragraphKey + "-" + (count + 1)
                ));
            }
            if (responseType.equals(QueryAgent.FinalReportResponse.class)) {
                return responseType.cast(new QueryAgent.FinalReportResponse("终稿：武汉大学樱花季舆情热度"));
            }

            throw new IllegalArgumentException("Unexpected class response type: " + responseType);
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            return callJson(clientName, systemPrompt, userPrompt, responseType);
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType) {
            calls.add(new Call(clientName, systemPrompt, userPrompt, responseType.getType().getTypeName()));

            @SuppressWarnings("unchecked")
            T result = (T) List.of(
                new QueryAgent.ParagraphPlan("结构规划1", "第一段内容范围"),
                new QueryAgent.ParagraphPlan("结构规划2", "第二段内容范围"),
                new QueryAgent.ParagraphPlan("结构规划3", "第三段内容范围")
            );
            return result;
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            return callJson(clientName, systemPrompt, userPrompt, responseType);
        }

        List<Call> calls() {
            return calls;
        }

        private String extractParagraphKey(String userPrompt) {
            int titleIndex = userPrompt.indexOf("段落标题：");
            if (titleIndex < 0) {
                return "unknown";
            }
            String titlePart = userPrompt.substring(titleIndex + "段落标题：".length());
            int lineEnd = titlePart.indexOf('\n');
            return (lineEnd >= 0 ? titlePart.substring(0, lineEnd) : titlePart).trim();
        }
    }

    private record Call(String clientName, String systemPrompt, String userPrompt, String responseType) {
    }

    private static final class FallbackOnlyLlmGateway implements LlmGateway {

        private int fallbackCount;

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType) {
            throw new UnsupportedOperationException("Fallback overload expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            fallbackCount++;
            return fallbackSupplier.get();
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType) {
            throw new UnsupportedOperationException("Fallback overload expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            fallbackCount++;
            return fallbackSupplier.get();
        }

        int fallbackCount() {
            return fallbackCount;
        }
    }

    private static final class RecordingPublisher implements AnalysisEventPublisher {

        private final List<AnalysisEvent> events = new ArrayList<>();

        @Override
        public void publish(AnalysisEvent event) {
            events.add(event);
        }

        List<AnalysisEvent> events() {
            return events;
        }
    }

    private static final class StaticForumGuidanceProvider implements ForumGuidanceProvider {

        private final ForumGuidance guidance = new ForumGuidance(
            1,
            "主持人指导",
            List.of("关注传播链路"),
            List.of("争议源头是什么"),
            List.of("证据缺口：源头样本"),
            "请优先补齐证据缺口并追问争议来源"
        );

        @Override
        public List<ForumMessage> transcript(String taskId) {
            return List.of(new ForumMessage("ForumHost", "host", guidance.promptAddendum(), Instant.parse("2026-03-18T00:00:00Z")));
        }

        @Override
        public List<ForumGuidance> guidanceHistory(String taskId) {
            return List.of(guidance);
        }
    }

    private static HttpServer startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", QueryAgentTest::handleTavilyRequest);
        server.start();
        return server;
    }

    private static void handleTavilyRequest(HttpExchange exchange) throws IOException {
        String response = """
            {
              "results": [
                {
                  "title": "武汉大学樱花季游客爆满",
                  "url": "https://news.sina.com.cn/c/2026-03-18/doc-query-1.shtml",
                  "content": "武汉大学樱花季热度继续攀升，校内游客接待压力明显增加。"
                },
                {
                  "title": "多平台讨论集中在预约与限流",
                  "url": "https://www.thepaper.cn/newsDetail_forward_32500001",
                  "content": "微博和短视频平台对预约机制与限流政策讨论较多。"
                },
                {
                  "title": "樱花季带动周边文旅消费",
                  "url": "https://www.huxiu.com/article/4000001.html",
                  "content": "文旅消费和社交平台讨论量同步上涨。"
                }
              ]
            }
            """;
        byte[] body = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static int indexOf(List<AnalysisEvent> events, Class<? extends AnalysisEvent> eventType) {
        for (int index = 0; index < events.size(); index++) {
            if (eventType.isInstance(events.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfSourceDelta(List<AnalysisEvent> events, int startExclusive) {
        for (int index = Math.max(0, startExclusive + 1); index < events.size(); index++) {
            AnalysisEvent event = events.get(index);
            if (event instanceof DeltaChunkEvent deltaChunkEvent && "search-sources".equals(deltaChunkEvent.channel())) {
                return index;
            }
        }
        return -1;
    }
}
