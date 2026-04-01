package com.bettafish.insight.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.llm.LlmGateway;
import com.fasterxml.jackson.core.type.TypeReference;

class KeywordOptimizerTest {

    @Test
    void usesGatewayStructuredOutputWhenAvailable() {
        KeywordOptimizer optimizer = new KeywordOptimizer(new StubLlmGateway(List.of("武汉大学樱花", "武汉大学樱花 评论", "武汉大学樱花 热度")));

        List<String> keywords = optimizer.optimize("武汉大学樱花");

        assertEquals(List.of("武汉大学樱花", "武汉大学樱花 评论", "武汉大学樱花 热度"), keywords);
    }

    @Test
    void fallsBackToDeterministicDefaultsWhenGatewayValidationFails() {
        KeywordOptimizer optimizer = new KeywordOptimizer(new InvalidKeywordGateway());

        List<String> keywords = optimizer.optimize("武汉大学樱花");

        assertEquals(List.of("武汉大学樱花", "武汉大学樱花 评论", "武汉大学樱花 热度"), keywords);
    }

    private static final class StubLlmGateway implements LlmGateway {

        private final List<String> response;

        private StubLlmGateway(List<String> response) {
            this.response = response;
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("TypeReference response expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            @SuppressWarnings("unchecked")
            T result = (T) response;
            return result;
        }
    }

    private static final class InvalidKeywordGateway implements LlmGateway {

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("TypeReference response expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            @SuppressWarnings("unchecked")
            T result = (T) List.of("", " ");
            return result;
        }
    }
}
