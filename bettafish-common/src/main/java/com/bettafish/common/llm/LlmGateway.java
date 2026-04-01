package com.bettafish.common.llm;

import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;

public interface LlmGateway {

    default String callText(String clientName, String systemPrompt, String userPrompt) {
        return callText(clientName, systemPrompt, userPrompt, null);
    }

    default String callText(String clientName, String systemPrompt, String userPrompt, Supplier<String> fallbackSupplier) {
        if (fallbackSupplier != null) {
            return fallbackSupplier.get();
        }
        throw new UnsupportedOperationException("callText is not implemented for this gateway");
    }

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType) {
        return callJson(clientName, systemPrompt, userPrompt, responseType, Validator.noop(), null);
    }

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                           Validator<T> validator) {
        return callJson(clientName, systemPrompt, userPrompt, responseType, validator, null);
    }

    <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                   Supplier<T> fallbackSupplier);

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, Class<T> responseType,
                           Validator<T> validator, Supplier<T> fallbackSupplier) {
        T result = callJson(clientName, systemPrompt, userPrompt, responseType, fallbackSupplier);
        return validateOrFallback(clientName, result, validator, fallbackSupplier);
    }

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType) {
        return callJson(clientName, systemPrompt, userPrompt, responseType, Validator.noop(), null);
    }

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                           Validator<T> validator) {
        return callJson(clientName, systemPrompt, userPrompt, responseType, validator, null);
    }

    <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                   Supplier<T> fallbackSupplier);

    default <T> T callJson(String clientName, String systemPrompt, String userPrompt, TypeReference<T> responseType,
                           Validator<T> validator, Supplier<T> fallbackSupplier) {
        T result = callJson(clientName, systemPrompt, userPrompt, responseType, fallbackSupplier);
        return validateOrFallback(clientName, result, validator, fallbackSupplier);
    }

    private static <T> T validateOrFallback(String clientName, T result, Validator<T> validator,
                                            Supplier<T> fallbackSupplier) {
        ValidationResult validationResult = validator == null ? ValidationResult.valid() : validator.validate(result);
        if (validationResult == null || validationResult.passed()) {
            return result;
        }
        if (fallbackSupplier != null) {
            return fallbackSupplier.get();
        }
        throw new IllegalStateException(
            "LLM returned invalid structured output for client %s: validation failed: %s".formatted(
                clientName,
                validationResult.errorDetail()
            )
        );
    }

    @FunctionalInterface
    interface Validator<T> {

        ValidationResult validate(T value);

        static <T> Validator<T> noop() {
            return ignored -> ValidationResult.valid();
        }
    }

    record ValidationResult(boolean passed, String errorDetail) {

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorDetail) {
            return new ValidationResult(false, errorDetail == null || errorDetail.isBlank() ? "unknown" : errorDetail);
        }
    }
}
