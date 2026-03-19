package com.bettafish.common.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        assertThrows(IllegalStateException.class, () ->
            invalidJsonGateway.callJson("queryChatClient", "system", "user", JsonAnswer.class)
        );
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

    private record JsonAnswer(@JsonProperty("answer") String answer) {
    }
}
