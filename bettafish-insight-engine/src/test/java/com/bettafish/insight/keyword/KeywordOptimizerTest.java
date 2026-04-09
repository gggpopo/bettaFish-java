package com.bettafish.insight.keyword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void badKeywords_areFiltered() {
        KeywordOptimizer optimizer = new KeywordOptimizer(new StubLlmGateway(List.of("武汉大学樱花", "舆情传播", "公众反应", "武汉大学樱花 热度")));

        List<String> keywords = optimizer.optimize("武汉大学樱花");

        assertEquals(List.of("武汉大学樱花", "武汉大学樱花 热度"), keywords);
    }

    @Test
    void longKeywords_areTruncated() {
        String longKeyword = "这是一个非常非常非常非常非常非常长的关键词短语";
        KeywordOptimizer optimizer = new KeywordOptimizer(new StubLlmGateway(List.of(longKeyword, "短词")));

        List<String> keywords = optimizer.optimize("测试");

        assertTrue(keywords.get(0).length() <= KeywordOptimizer.MAX_KEYWORD_LENGTH);
        assertEquals("短词", keywords.get(1));
    }

    @Test
    void optimizeWithContext_includesContextInPrompt() {
        CapturingLlmGateway gateway = new CapturingLlmGateway();
        KeywordOptimizer optimizer = new KeywordOptimizer(gateway);

        optimizer.optimize("武汉大学樱花", "近期樱花季引发关注");

        assertTrue(gateway.capturedUserPrompt.contains("近期樱花季引发关注"));
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

    private static final class CapturingLlmGateway implements LlmGateway {

        String capturedUserPrompt;

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException("TypeReference response expected");
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                              java.util.function.Supplier<T> fallbackSupplier) {
            capturedUserPrompt = userPrompt;
            @SuppressWarnings("unchecked")
            T result = (T) List.of("关键词A", "关键词B");
            return result;
        }
    }
}
