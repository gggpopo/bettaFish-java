package com.bettafish.common.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.bettafish.common.engine.ExecutionCancelledException;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ExecutionContextHolder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpringAiLlmGatewayTest {

    @Test
    void returnsPlainTextContent() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("plain text answer");

        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());

        String answer = gateway.callText("queryChatClient", "system", "user");

        assertEquals("plain text answer", answer);
    }

    @Test
    void abortsBlockedTextCallWhenExecutionIsCancelled() throws Exception {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        CountDownLatch started = new CountDownLatch(1);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                return "unreachable";
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", ex);
            }
        });

        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());
        ExecutionContext executionContext = new ExecutionContext(Duration.ofMinutes(1));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> ExecutionContextHolder.callWith(
                executionContext,
                () -> gateway.callText("queryChatClient", "system", "user")
            ));

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(executionContext.cancel());

            ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertInstanceOf(ExecutionCancelledException.class, exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void parsesJsonWrappedInMarkdownFence() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
            ```json
            {"answer":"ok"}
            ```
            """);

        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());

        JsonAnswer answer = gateway.callJson("queryChatClient", "system", "user", JsonAnswer.class);

        assertEquals("ok", answer.answer());
    }

    @Test
    void rejectsMissingClientOrInvalidJson() {
        SpringAiLlmGateway missingClientGateway = new SpringAiLlmGateway(Map.of(), new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () ->
            missingClientGateway.callJson("queryChatClient", "system", "user", JsonAnswer.class)
        );

        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("not-json");

        SpringAiLlmGateway invalidJsonGateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            invalidJsonGateway.callJson("queryChatClient", "system", "user", JsonAnswer.class)
        );

        assertTrue(exception.getMessage().contains("LLM returned invalid JSON"));
        assertTrue(exception.getMessage().contains("extracted="));
        assertTrue(exception.getMessage().contains("repaired="));
    }

    @Test
    void downgradesToFallbackWhenJsonCannotBeParsed() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("broken-json");

        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());

        JsonAnswer fallback = gateway.callJson(
            "queryChatClient",
            "system",
            "user",
            JsonAnswer.class,
            () -> new JsonAnswer("fallback")
        );

        assertEquals("fallback", fallback.answer());
    }

    @Test
    void downgradesToFallbackWhenClientIsMissing() {
        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of(), new ObjectMapper());

        JsonAnswer fallback = gateway.callJson(
            "queryChatClient",
            "system",
            "user",
            JsonAnswer.class,
            () -> new JsonAnswer("missing-client")
        );

        assertEquals("missing-client", fallback.answer());
    }

    @Test
    void downgradesToFallbackWhenValidationFails() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
            {"answer":"   "}
            """);

        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());

        JsonAnswer fallback = gateway.callJson(
            "queryChatClient",
            "system",
            "user",
            JsonAnswer.class,
            answer -> answer.answer() == null || answer.answer().isBlank()
                ? LlmGateway.ValidationResult.invalid("answer must not be blank")
                : LlmGateway.ValidationResult.valid(),
            () -> new JsonAnswer("validated-fallback")
        );

        assertEquals("validated-fallback", fallback.answer());
    }

    @Test
    void doesNotDowngradeToFallbackWhenExecutionIsCancelled() throws Exception {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        CountDownLatch started = new CountDownLatch(1);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                return "{\"answer\":\"late\"}";
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", ex);
            }
        });

        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());
        ExecutionContext executionContext = new ExecutionContext(Duration.ofMinutes(1));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JsonAnswer> future = executor.submit(() -> ExecutionContextHolder.callWith(
                executionContext,
                () -> gateway.callJson(
                    "queryChatClient",
                    "system",
                    "user",
                    JsonAnswer.class,
                    () -> new JsonAnswer("fallback")
                )
            ));

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(executionContext.cancel());

            ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertInstanceOf(ExecutionCancelledException.class, exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void raisesValidationFailureWithoutFallback() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
            {"answer":"   "}
            """);

        SpringAiLlmGateway gateway = new SpringAiLlmGateway(Map.of("queryChatClient", chatClient), new ObjectMapper());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            gateway.callJson(
                "queryChatClient",
                "system",
                "user",
                JsonAnswer.class,
                answer -> answer.answer() == null || answer.answer().isBlank()
                    ? LlmGateway.ValidationResult.invalid("answer must not be blank")
                    : LlmGateway.ValidationResult.valid()
            )
        );

        assertTrue(exception.getMessage().contains("validation failed"));
        assertTrue(exception.getMessage().contains("answer must not be blank"));
    }

    private record JsonAnswer(@JsonProperty("answer") String answer) {
    }
}
