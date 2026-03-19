package com.bettafish.common.llm;

import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;

public interface LlmGateway {

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType) {
        return callJson(clientName, systemPrompt, userPrompt, responseType, null);
    }

    <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                   Supplier<T> fallbackSupplier);

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType) {
        return callJson(clientName, systemPrompt, userPrompt, responseType, null);
    }

    <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                   Supplier<T> fallbackSupplier);
}
