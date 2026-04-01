package com.bettafish.common.llm;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SpringAiLlmGateway implements LlmGateway {

    private static final String JSON_ONLY_SUFFIX = """
        你必须只返回一个合法 JSON 值。
        不要输出 markdown 代码块，不要输出解释性文字，不要添加 JSON 前后缀。
        """;

    private final Map<String, ChatClient> chatClients;
    private final JsonOutputParser jsonOutputParser;

    public SpringAiLlmGateway(Map<String, ChatClient> chatClients, ObjectMapper objectMapper) {
        this.chatClients = chatClients;
        this.jsonOutputParser = new JsonOutputParser(objectMapper);
    }

    @Override
    public String callText(String clientName, String systemPrompt, String userPrompt, Supplier<String> fallbackSupplier) {
        try {
            return requestContent(clientName, systemPrompt, userPrompt, false);
        } catch (RuntimeException ex) {
            return fallbackOrThrow(fallbackSupplier, ex);
        }
    }

    @Override
    public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                          Supplier<T> fallbackSupplier) {
        return execute(
            clientName,
            systemPrompt,
            userPrompt,
            raw -> jsonOutputParser.parse(raw, responseType),
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
            raw -> jsonOutputParser.parse(raw, responseType),
            fallbackSupplier
        );
    }

    private <T> T execute(String clientName, String systemPrompt, String userPrompt,
                          Function<String, JsonOutputParser.ParseResult<T>> parser, Supplier<T> fallbackSupplier) {
        try {
            String raw = requestContent(clientName, systemPrompt, userPrompt, true);
            JsonOutputParser.ParseResult<T> parseResult = parser.apply(raw);
            if (parseResult.success()) {
                return parseResult.value();
            }

            return fallbackOrThrow(fallbackSupplier, invalidJson(clientName, parseResult));
        } catch (RuntimeException ex) {
            return fallbackOrThrow(fallbackSupplier, ex);
        }
    }

    private String requestContent(String clientName, String systemPrompt, String userPrompt, boolean jsonOnly) {
        ChatClient chatClient = chatClients.get(clientName);
        if (chatClient == null) {
            throw new IllegalArgumentException("No ChatClient registered for: " + clientName);
        }

        String normalizedSystemPrompt = jsonOnly ? normalizeJsonSystemPrompt(systemPrompt) : systemPrompt;
        String raw = chatClient.prompt()
            .system(normalizedSystemPrompt)
            .user(userPrompt)
            .call()
            .content();

        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("LLM returned empty content for client: " + clientName);
        }
        return raw;
    }

    private String normalizeJsonSystemPrompt(String systemPrompt) {
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

    private IllegalStateException invalidJson(String clientName, JsonOutputParser.ParseResult<?> parseResult) {
        String extractedPreview = preview(parseResult.extractedJson());
        String repairedPreview = preview(parseResult.repairedJson());
        String rawPreview = preview(parseResult.rawInput());
        return new IllegalStateException(
            "LLM returned invalid JSON for client %s: %s | extracted=%s | repaired=%s | repairs=%s | raw=%s".formatted(
                clientName,
                parseResult.errorDetail(),
                extractedPreview,
                repairedPreview,
                parseResult.repairActions(),
                rawPreview
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
