# Json Output Parser Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace silent JSON parsing failure paths with a repairable, observable `JsonOutputParser` in `bettafish-common/common/llm`, and wire `SpringAiLlmGateway` to use it for structured model output.

**Architecture:** Introduce a dedicated `JsonOutputParser` that owns extraction, cleanup, repair, and failure reporting for LLM JSON output. Keep `common/util/JsonParser` as a compatibility facade that delegates to the new parser, but change `parseObject()` so it no longer returns an empty map on failure. Add a dedicated `JsonOutputParsingException` carrying raw text and parser diagnostics, then switch `SpringAiLlmGateway` to use the new parser for both class and `TypeReference` responses.

**Tech Stack:** Java 21, Jackson, Spring AI, JUnit 5

### Task 1: Add Failing Tests For New Parser Behavior

**Files:**
- Create: `bettafish-common/src/test/java/com/bettafish/common/llm/JsonOutputParserTest.java`
- Modify: `bettafish-common/src/test/java/com/bettafish/common/util/JsonParserTest.java`
- Modify: `bettafish-common/src/test/java/com/bettafish/common/llm/SpringAiLlmGatewayTest.java`

**Step 1: Write the failing test**

Add tests that expect:
- fenced `json` block extraction
- surrounding explanation/thinking text cleanup
- trailing comma / missing closer repair
- thrown exception with raw text and detail on unrecoverable input
- `JsonParser.parseObject()` to throw instead of returning an empty map
- `SpringAiLlmGateway` exception messages to include parser diagnostics

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JsonOutputParserTest,JsonParserTest,SpringAiLlmGatewayTest test`
Expected: FAIL because `JsonOutputParser` and the new failure behavior do not exist.

### Task 2: Implement JsonOutputParser And Exception

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/llm/JsonOutputParser.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/llm/JsonOutputParsingException.java`

**Step 1: Write minimal implementation**

Implement:
- fenced block extraction
- removal of model thinking/explanation text
- first balanced `{...}` / `[...]` candidate extraction
- common repair attempts for trailing commas and missing closing brackets
- result object containing cleaned/extracted/repaired/error details
- `parseOrThrow(...)` methods that throw `JsonOutputParsingException`

**Step 2: Run tests to verify progress**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JsonOutputParserTest test`
Expected: PASS

### Task 3: Migrate Gateway And Compatibility Facade

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/llm/SpringAiLlmGateway.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/util/JsonParser.java`

**Step 1: Write minimal implementation**

Use `JsonOutputParser` inside `SpringAiLlmGateway`, keep fallback behavior, and preserve rich failure detail. Make `JsonParser.parse(...)` delegate to the new parser while keeping its `ParseResult` shape; make `parseObject()` throw on failure.

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=JsonOutputParserTest,JsonParserTest,SpringAiLlmGatewayTest test`
Expected: PASS

### Task 4: Full Verification

**Files:**
- Modify only files above as needed

**Step 1: Run full test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`
Expected: PASS
