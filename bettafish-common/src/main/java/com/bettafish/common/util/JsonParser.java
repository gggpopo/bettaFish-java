package com.bettafish.common.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonParser() {
    }

    public static Map<String, Object> parseObject(String json) {
        ParseResult<Map<String, Object>> result = parse(json, new TypeReference<Map<String, Object>>() {
        });
        return result.success() ? result.value() : Collections.emptyMap();
    }

    public static <T> ParseResult<T> parse(String raw, Class<T> type) {
        return parseInternal(raw, candidate -> OBJECT_MAPPER.readValue(candidate, type));
    }

    public static <T> ParseResult<T> parse(String raw, TypeReference<T> type) {
        return parseInternal(raw, candidate -> OBJECT_MAPPER.readValue(candidate, type));
    }

    private static <T> ParseResult<T> parseInternal(String raw, JsonReader<T> reader) {
        String rawInput = raw == null ? "" : raw;
        String cleanedInput = cleanThinkingText(rawInput);
        String extractedJson = extractJsonCandidate(cleanedInput);

        if (extractedJson.isBlank()) {
            return new ParseResult<>(
                false,
                null,
                rawInput,
                cleanedInput,
                "",
                "",
                List.of(),
                "No JSON object or array found in model output"
            );
        }

        ParseAttempt<T> directAttempt = tryRead(extractedJson, reader);
        if (directAttempt.success()) {
            return new ParseResult<>(
                true,
                directAttempt.value(),
                rawInput,
                cleanedInput,
                extractedJson,
                extractedJson,
                List.of(),
                null
            );
        }

        RepairResult repairResult = repairMinorIssues(extractedJson);
        if (!repairResult.repairedJson().equals(extractedJson)) {
            ParseAttempt<T> repairedAttempt = tryRead(repairResult.repairedJson(), reader);
            if (repairedAttempt.success()) {
                return new ParseResult<>(
                    true,
                    repairedAttempt.value(),
                    rawInput,
                    cleanedInput,
                    extractedJson,
                    repairResult.repairedJson(),
                    repairResult.repairActions(),
                    null
                );
            }

            return new ParseResult<>(
                false,
                null,
                rawInput,
                cleanedInput,
                extractedJson,
                repairResult.repairedJson(),
                repairResult.repairActions(),
                "Initial parse failed: " + directAttempt.errorDetail()
                    + " | Repaired parse failed: " + repairedAttempt.errorDetail()
            );
        }

        return new ParseResult<>(
            false,
            null,
            rawInput,
            cleanedInput,
            extractedJson,
            extractedJson,
            List.of(),
            directAttempt.errorDetail()
        );
    }

    private static String cleanThinkingText(String rawInput) {
        return rawInput
            .replaceAll("(?is)<think>.*?</think>", " ")
            .replaceAll("(?im)^```json\\s*$", "")
            .replaceAll("(?im)^```\\s*$", "")
            .trim();
    }

    private static String extractJsonCandidate(String cleanedInput) {
        int start = firstJsonStart(cleanedInput);
        if (start < 0) {
            return "";
        }

        int end = findBalancedJsonEnd(cleanedInput, start);
        if (end >= start) {
            return cleanedInput.substring(start, end + 1).trim();
        }
        return cleanedInput.substring(start).trim();
    }

    private static int firstJsonStart(String input) {
        int objectStart = input.indexOf('{');
        int arrayStart = input.indexOf('[');
        if (objectStart < 0) {
            return arrayStart;
        }
        if (arrayStart < 0) {
            return objectStart;
        }
        return Math.min(objectStart, arrayStart);
    }

    private static int findBalancedJsonEnd(String input, int start) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaping = false;

        for (int index = start; index < input.length(); index++) {
            char ch = input.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = inString;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }

            if (ch == '{' || ch == '[') {
                stack.push(ch);
                continue;
            }
            if (ch == '}' || ch == ']') {
                if (stack.isEmpty()) {
                    return index;
                }
                char open = stack.pop();
                if (!isMatchingBracket(open, ch)) {
                    return index;
                }
                if (stack.isEmpty()) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean isMatchingBracket(char open, char close) {
        return (open == '{' && close == '}') || (open == '[' && close == ']');
    }

    private static RepairResult repairMinorIssues(String jsonCandidate) {
        List<String> repairActions = new ArrayList<>();
        String repaired = jsonCandidate;

        String withoutCommaBeforeClose = repaired.replaceAll(",(?=\\s*[}\\]])", "");
        if (!withoutCommaBeforeClose.equals(repaired)) {
            repairActions.add("removed trailing commas before closing bracket");
            repaired = withoutCommaBeforeClose;
        }

        String withoutDanglingTailComma = repaired.replaceAll(",\\s*$", "");
        if (!withoutDanglingTailComma.equals(repaired)) {
            repairActions.add("removed dangling comma at end of payload");
            repaired = withoutDanglingTailComma;
        }

        MissingClosers missingClosers = detectMissingClosers(repaired);
        if (!missingClosers.appendedClosers().isEmpty()) {
            repairActions.add("appended missing closing brackets: " + missingClosers.appendedClosers());
            repaired = repaired + missingClosers.appendedClosers();
        }

        return new RepairResult(repaired, List.copyOf(repairActions));
    }

    private static MissingClosers detectMissingClosers(String input) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaping = false;

        for (int index = 0; index < input.length(); index++) {
            char ch = input.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = inString;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }

            if (ch == '{' || ch == '[') {
                stack.push(ch);
            } else if ((ch == '}' || ch == ']') && !stack.isEmpty() && isMatchingBracket(stack.peek(), ch)) {
                stack.pop();
            }
        }

        StringBuilder appended = new StringBuilder();
        while (!stack.isEmpty()) {
            appended.append(stack.pop() == '{' ? '}' : ']');
        }
        return new MissingClosers(appended.toString());
    }

    private static <T> ParseAttempt<T> tryRead(String candidate, JsonReader<T> reader) {
        try {
            return ParseAttempt.success(reader.read(candidate));
        } catch (JsonProcessingException ex) {
            return ParseAttempt.failure(ex.getOriginalMessage());
        }
    }

    private interface JsonReader<T> {

        T read(String candidate) throws JsonProcessingException;
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

    private record ParseAttempt<T>(boolean success, T value, String errorDetail) {

        private static <T> ParseAttempt<T> success(T value) {
            return new ParseAttempt<>(true, value, null);
        }

        private static <T> ParseAttempt<T> failure(String errorDetail) {
            return new ParseAttempt<>(false, null, errorDetail);
        }
    }

    private record RepairResult(String repairedJson, List<String> repairActions) {
    }

    private record MissingClosers(String appendedClosers) {
    }
}
