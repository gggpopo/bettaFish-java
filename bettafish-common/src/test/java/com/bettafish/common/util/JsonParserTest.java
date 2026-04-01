package com.bettafish.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.llm.JsonOutputParsingException;
import com.fasterxml.jackson.annotation.JsonProperty;

class JsonParserTest {

    @Test
    void extractsJsonFromThinkingTextAndMarkdownFence() {
        JsonParser.ParseResult<Decision> result = JsonParser.parse("""
            <think>
            先分析上下文，再构造答案
            </think>
            ```json
            {"should_refine":true,"reasoning":"need more data"}
            ```
            以上是最终答案
            """, Decision.class);

        assertTrue(result.success());
        assertTrue(result.cleanedInput().contains("{\"should_refine\":true"));
        assertEquals("{\"should_refine\":true,\"reasoning\":\"need more data\"}", result.extractedJson());
        assertTrue(result.repairActions().isEmpty());
        assertTrue(result.value().shouldRefine());
    }

    @Test
    void repairsTrailingCommaAndMissingClosingBrace() {
        JsonParser.ParseResult<Decision> result = JsonParser.parse("""
            思考完成，答案如下：
            {"should_refine":false,"reasoning":"good enough",
            """, Decision.class);

        assertTrue(result.success());
        assertFalse(result.repairActions().isEmpty());
        assertFalse(result.value().shouldRefine());
        assertTrue(result.repairedJson().endsWith("}"));
    }

    @Test
    void exposesDetailedFailureForUnrecoverableInput() {
        JsonParser.ParseResult<Decision> result = JsonParser.parse("not-json-at-all", Decision.class);

        assertFalse(result.success());
        assertTrue(result.errorDetail().contains("No JSON"));
        assertEquals(List.of(), result.repairActions());
    }

    @Test
    void parseObjectThrowsInsteadOfReturningEmptyMap() {
        JsonOutputParsingException exception = assertThrows(JsonOutputParsingException.class, () ->
            JsonParser.parseObject("not-json-at-all")
        );

        assertEquals("not-json-at-all", exception.rawText());
        assertTrue(exception.errorDetail().contains("No JSON"));
    }

    @Test
    void parseObjectStillReturnsMapOnSuccess() {
        Map<String, Object> result = JsonParser.parseObject("""
            ```json
            {"answer":"ok"}
            ```
            """);

        assertEquals("ok", result.get("answer"));
    }

    private record Decision(@JsonProperty("should_refine") boolean shouldRefine,
                            @JsonProperty("reasoning") String reasoning) {
    }
}
