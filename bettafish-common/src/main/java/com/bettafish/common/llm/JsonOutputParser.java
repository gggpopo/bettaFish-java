package com.bettafish.common.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonOutputParser {

    private static final Pattern FENCED_BLOCK_PATTERN = Pattern.compile("(?is)```(?:json)?\\s*(.*?)\\s*```");

    private final ObjectMapper objectMapper;

    public JsonOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> ParseResult<T> parse(String raw, Class<T> type) {
        return parseInternal(raw, candidate -> objectMapper.readValue(candidate, type));
    }

    public <T> ParseResult<T> parse(String raw, TypeReference<T> type) {
        return parseInternal(raw, candidate -> objectMapper.readValue(candidate, type));
    }

    public <T> T parseOrThrow(String raw, Class<T> type) {
        ParseResult<T> result = parse(raw, type);
        if (result.success()) {
            return result.value();
        }
        throw failure(result);
    }

    public <T> T parseOrThrow(String raw, TypeReference<T> type) {
        ParseResult<T> result = parse(raw, type);
        if (result.success()) {
            return result.value();
        }
        throw failure(result);
    }

    private <T> ParseResult<T> parseInternal(String raw, JsonReader<T> reader) {
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

    private JsonOutputParsingException failure(ParseResult<?> result) {
        return new JsonOutputParsingException(
            "Failed to parse JSON output: %s | repairs=%s | raw=%s".formatted(
                result.errorDetail(),
                result.repairActions(),
                preview(result.rawInput())
            ),
            result.rawInput(),
            result.cleanedInput(),
            result.extractedJson(),
            result.repairedJson(),
            result.repairActions(),
            result.errorDetail()
        );
    }

    private String cleanThinkingText(String rawInput) {
        return rawInput
            .replaceAll("(?is)<think>.*?</think>", " ")
            .trim();
    }

    private String extractJsonCandidate(String cleanedInput) {
        String fencedCandidate = extractFromFencedBlock(cleanedInput);
        if (!fencedCandidate.isBlank()) {
            return fencedCandidate;
        }

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

    private String extractFromFencedBlock(String input) {
        Matcher matcher = FENCED_BLOCK_PATTERN.matcher(input);
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            int start = firstJsonStart(candidate);
            if (start < 0) {
                continue;
            }
            int end = findBalancedJsonEnd(candidate, start);
            if (end >= start) {
                return candidate.substring(start, end + 1).trim();
            }
            return candidate.substring(start).trim();
        }
        return "";
    }

    private int firstJsonStart(String input) {
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

    private int findBalancedJsonEnd(String input, int start) {
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

    private boolean isMatchingBracket(char open, char close) {
        return (open == '{' && close == '}') || (open == '[' && close == ']');
    }

    private RepairResult repairMinorIssues(String jsonCandidate) {
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

    private MissingClosers detectMissingClosers(String input) {
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

    private <T> ParseAttempt<T> tryRead(String candidate, JsonReader<T> reader) {
        try {
            return ParseAttempt.success(reader.read(candidate));
        } catch (JsonProcessingException ex) {
            return ParseAttempt.failure(ex.getOriginalMessage());
        }
    }

    private String preview(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.length() > 240 ? value.substring(0, 240) + "..." : value;
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
