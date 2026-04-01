package com.bettafish.common.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
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
