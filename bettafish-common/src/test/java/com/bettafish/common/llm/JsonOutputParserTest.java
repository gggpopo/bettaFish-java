package com.bettafish.common.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonOutputParserTest {

    private final JsonOutputParser parser = new JsonOutputParser(new ObjectMapper());

    @Test
    void extractsJsonFromFencedBlockAndExplanationText() {
        JsonOutputParser.ParseResult<Decision> result = parser.parse("""
            先给出分析，再给出最终 JSON：
            ```json
            {"should_refine":true,"reasoning":"need more data"}
            ```
            以上就是最终答案。
            """, Decision.class);

        assertTrue(result.success());
        assertEquals("{\"should_refine\":true,\"reasoning\":\"need more data\"}", result.extractedJson());
        assertTrue(result.repairActions().isEmpty());
    }

    @Test
    void repairsTrailingCommaAndMissingCloser() {
        JsonOutputParser.ParseResult<Decision> result = parser.parse("""
            解释文字
            {"should_refine":false,"reasoning":"good enough",
            """, Decision.class);

        assertTrue(result.success());
        assertFalse(result.repairActions().isEmpty());
        assertTrue(result.repairedJson().endsWith("}"));
        assertFalse(result.value().shouldRefine());
    }

    @Test
    void throwsDetailedExceptionForUnrecoverableInput() {
        JsonOutputParsingException exception = assertThrows(JsonOutputParsingException.class, () ->
            parser.parseOrThrow("not-json-at-all", Decision.class)
        );

        assertTrue(exception.getMessage().contains("No JSON"));
        assertEquals("not-json-at-all", exception.rawText());
        assertTrue(exception.errorDetail().contains("No JSON"));
    }

    private record Decision(@JsonProperty("should_refine") boolean shouldRefine,
                            @JsonProperty("reasoning") String reasoning) {
    }
}
