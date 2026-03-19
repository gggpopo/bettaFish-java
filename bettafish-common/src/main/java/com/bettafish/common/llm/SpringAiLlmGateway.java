package com.bettafish.common.llm;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bettafish.common.util.JsonParser;

@Component
public class SpringAiLlmGateway implements LlmGateway {

    private static final String JSON_ONLY_SUFFIX = """
        你必须只返回一个合法 JSON 值。
        不要输出 markdown 代码块，不要输出解释性文字，不要添加 JSON 前后缀。
        """;

    private final Map<String, ChatClient> chatClients;
    private final ObjectMapper objectMapper;

    public SpringAiLlmGateway(Map<String, ChatClient> chatClients, ObjectMapper objectMapper) {
        this.chatClients = chatClients;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                          Supplier<T> fallbackSupplier) {
        return execute(
            clientName,
            systemPrompt,
            userPrompt,
            raw -> JsonParser.parse(raw, responseType),
            fallbackSupplier
        );
    }

    @Override
    public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                          Supplier<T> fallbackSupplier) {
        return execute(
            clientName,
            systemPrompt,
            userPrompt,
            raw -> JsonParser.parse(raw, responseType),
            fallbackSupplier
        );
    }

    private <T> T execute(String clientName, String systemPrompt, String userPrompt,
                          Function<String, JsonParser.ParseResult<T>> parser, Supplier<T> fallbackSupplier) {
        ChatClient chatClient = chatClients.get(clientName);
        if (chatClient == null) {
            return fallbackOrThrow(
                fallbackSupplier,
                new IllegalArgumentException("No ChatClient registered for: " + clientName)
            );
        }

        try {
            String raw = chatClient.prompt()
                .system(normalizeSystemPrompt(systemPrompt))
                .user(userPrompt)
                .call()
                .content();

            if (raw == null || raw.isBlank()) {
                return fallbackOrThrow(
                    fallbackSupplier,
                    new IllegalStateException("LLM returned empty content for client: " + clientName)
                );
            }

            JsonParser.ParseResult<T> parseResult = parser.apply(raw);
            if (parseResult.success()) {
                return parseResult.value();
            }

            return fallbackOrThrow(fallbackSupplier, invalidJson(clientName, parseResult));
        } catch (RuntimeException ex) {
            return fallbackOrThrow(fallbackSupplier, ex);
        }
    }

    private String normalizeSystemPrompt(String systemPrompt) {
        return systemPrompt == null || systemPrompt.isBlank()
            ? JSON_ONLY_SUFFIX
            : systemPrompt + "\n\n" + JSON_ONLY_SUFFIX;
    }

    private <T> T fallbackOrThrow(Supplier<T> fallbackSupplier, RuntimeException exception) {
        if (fallbackSupplier != null) {
            return fallbackSupplier.get();
        }
        throw exception;
    }

    private IllegalStateException invalidJson(String clientName, JsonParser.ParseResult<?> parseResult) {
        String extractedPreview = preview(parseResult.extractedJson());
        String repairedPreview = preview(parseResult.repairedJson());
        return new IllegalStateException(
            "LLM returned invalid JSON for client %s: %s | extracted=%s | repaired=%s | repairs=%s".formatted(
                clientName,
                parseResult.errorDetail(),
                extractedPreview,
                repairedPreview,
                parseResult.repairActions()
            )
        );
    }

    private String preview(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.length() > 240 ? value.substring(0, 240) + "..." : value;
    }
}
