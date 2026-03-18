package com.bettafish.common.util;

import java.util.Collections;
import java.util.Map;
import org.springframework.boot.json.JsonParseException;
import org.springframework.boot.json.JsonParserFactory;

public final class JsonParser {

    private static final org.springframework.boot.json.JsonParser PARSER = JsonParserFactory.getJsonParser();

    private JsonParser() {
    }

    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return PARSER.parseMap(json);
        } catch (JsonParseException ex) {
            return Collections.emptyMap();
        }
    }
}
