package com.bettafish.common.util;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bettafish.common.llm.JsonOutputParser;
import com.bettafish.common.llm.JsonOutputParsingException;

public final class JsonParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonOutputParser OUTPUT_PARSER = new JsonOutputParser(OBJECT_MAPPER);

    private JsonParser() {
    }

    public static Map<String, Object> parseObject(String json) {
        return OUTPUT_PARSER.parseOrThrow(json, new TypeReference<Map<String, Object>>() {
        });
    }

    public static <T> ParseResult<T> parse(String raw, Class<T> type) {
        return toLegacyResult(OUTPUT_PARSER.parse(raw, type));
    }

    public static <T> ParseResult<T> parse(String raw, TypeReference<T> type) {
        return toLegacyResult(OUTPUT_PARSER.parse(raw, type));
    }

    public static <T> T parseOrThrow(String raw, Class<T> type) throws JsonOutputParsingException {
        return OUTPUT_PARSER.parseOrThrow(raw, type);
    }

    public static <T> T parseOrThrow(String raw, TypeReference<T> type) throws JsonOutputParsingException {
        return OUTPUT_PARSER.parseOrThrow(raw, type);
    }

    private static <T> ParseResult<T> toLegacyResult(JsonOutputParser.ParseResult<T> result) {
        return new ParseResult<>(
            result.success(),
            result.value(),
            result.rawInput(),
            result.cleanedInput(),
            result.extractedJson(),
            result.repairedJson(),
            result.repairActions(),
            result.errorDetail()
        );
    }

    public record ParseResult<T>(
        boolean success,
        T value,
        String rawInput,
        String cleanedInput,
        String extractedJson,
        String repairedJson,
        List<String> repairActions,
        String errorDetail
    ) {
    }

}
