package com.bettafish.app.controller;

import com.jayway.jsonpath.JsonPath;

final class JsonTestSupport {

    private JsonTestSupport() {
    }

    static String readJsonPath(String json, String path) {
        return JsonPath.read(json, path);
    }
}
