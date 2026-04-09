package com.bettafish.query.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.engine.ExecutionCancelledException;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ExecutionContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class TavilySearchToolTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsTavilyHttpResultsToRealSourceReferences() throws Exception {
        RecordingHandler handler = new RecordingHandler("""
            {
              "results": [
                {
                  "title": "武汉大学樱花季游客爆满",
                  "url": "https://news.sina.com.cn/c/2026-03-18/doc-example1.shtml",
                  "content": "武汉大学樱花季热度继续攀升，校内游客接待压力明显增加。"
                },
                {
                  "title": "樱花季带动周边文旅消费",
                  "url": "https://www.thepaper.cn/newsDetail_forward_32500001",
                  "content": "文旅消费和社交平台讨论量同步上涨。"
                },
                {
                  "title": "多平台讨论集中在预约与限流",
                  "url": "https://www.huxiu.com/article/4000001.html",
                  "content": "微博和短视频平台对预约机制与限流政策讨论较多。"
                }
              ]
            }
            """);
        server = startServer(handler);

        TavilySearchTool tool = new TavilySearchTool(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            "http://localhost:" + server.getAddress().getPort(),
            "tvly-test-key",
            3
        );

        List<SourceReference> sources = tool.search("武汉大学樱花季舆情热度");

        assertEquals(3, sources.size());
        assertTrue(sources.stream().noneMatch(source -> source.url().contains("example.com")));
        assertTrue(sources.stream().allMatch(source -> source.url().startsWith("https://")));
        assertTrue(handler.requestBody().contains("\"query\":\"武汉大学樱花季舆情热度\""));
        assertTrue(handler.requestBody().contains("\"api_key\":\"tvly-test-key\""));
        assertTrue(handler.requestBody().contains("\"max_results\":3"));
    }

    @Test
    void abortsBlockedHttpSearchWhenExecutionIsCancelled() throws Exception {
        BlockingHttpClient httpClient = new BlockingHttpClient();
        TavilySearchTool tool = new TavilySearchTool(
            httpClient,
            new ObjectMapper(),
            "http://localhost:8080",
            "tvly-test-key",
            3
        );
        ExecutionContext executionContext = new ExecutionContext(Duration.ofMinutes(1));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<SourceReference>> future = executor.submit(() -> ExecutionContextHolder.callWith(
                executionContext,
                () -> tool.search("阻塞中的 Tavily 请求")
            ));

            assertTrue(httpClient.awaitStarted());
            assertTrue(executionContext.cancel());

            ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertInstanceOf(ExecutionCancelledException.class, exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private HttpServer startServer(RecordingHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/search", handler);
        httpServer.start();
        return httpServer;
    }

    private static final class RecordingHandler implements com.sun.net.httpserver.HttpHandler {

        private final byte[] responseBody;
        private volatile String requestBody = "";

        private RecordingHandler(String responseBody) {
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        }

        private String requestBody() {
            return requestBody;
        }
    }

    private static final class BlockingHttpClient extends HttpClient {

        private final CountDownLatch started = new CountDownLatch(1);

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
            started.countDown();
            new CountDownLatch(1).await();
            throw new IllegalStateException("unreachable");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(1, TimeUnit.SECONDS);
        }
    }
}
