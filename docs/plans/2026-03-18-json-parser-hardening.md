# JSON Parser Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Harden `JsonParser` and `LlmGateway` so LLM JSON output can be extracted from noisy text, minimally repaired, observed with detailed parse diagnostics, and safely downgraded with fallback decisions.

**Architecture:** Move JSON cleanup and repair logic into `bettafish-common` `JsonParser`, expose a structured parse result containing raw input, extracted candidate, repaired candidate, repair actions, and failure detail, then refactor `SpringAiLlmGateway` to use it and support typed fallback suppliers. Update `QueryAgent` to supply deterministic fallback structures, summaries, decisions, and final reports.

**Tech Stack:** Java 21, Jackson `ObjectMapper`, Spring AI `ChatClient`, JUnit 5, Mockito, Maven.

### Task 1: Lock expected parser and fallback behavior in tests

**Files:**
- Create: `bettafish-common/src/test/java/com/bettafish/common/util/JsonParserTest.java`
- Modify: `bettafish-common/src/test/java/com/bettafish/common/llm/SpringAiLlmGatewayTest.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`

**Step 1: Write the failing test**

Add parser tests that prove:
- JSON can be extracted from fenced output with `<think>` blocks and trailing prose
- trailing commas and missing closing brackets are repaired
- unrecoverable payloads return detailed parse failure information

Extend gateway tests to prove:
- invalid JSON can downgrade via fallback supplier
- missing client can also downgrade via fallback supplier

Extend Query tests to prove:
- `QueryAgent` completes when gateway calls downgrade to fallback decisions and fallback final report content

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine test`

Expected: FAIL because parser observability and fallback overloads do not exist yet.

### Task 2: Upgrade `JsonParser` to an observable parser

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/util/JsonParser.java`

**Step 1: Write minimal implementation**

Implement:
- typed `parse(...)` overloads for `Class<T>` and `TypeReference<T>`
- `ParseResult<T>` with success flag, parsed value, raw input, cleaned input, extracted candidate, repaired candidate, repair actions, and error detail
- extraction of JSON body from noisy output
- cleaning of common thinking wrappers such as `<think>...</think>` and surrounding prose
- local repair for trailing commas and missing closing brackets

**Step 2: Run test to verify parser tests pass**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common test`

Expected: PASS for parser and existing common tests.

### Task 3: Add gateway fallback overloads and wire Query defaults

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/llm/LlmGateway.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/llm/SpringAiLlmGateway.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`

**Step 1: Write minimal implementation**

Implement:
- `callJson(..., fallbackSupplier)` overloads in `LlmGateway`
- `SpringAiLlmGateway` using `JsonParser.ParseResult` for structured diagnostics
- fallback execution for missing client, empty content, or unrecoverable JSON
- deterministic Query fallbacks for paragraph planning, summary, reflection decision, reflection summary, and final report

**Step 2: Run test to verify query and gateway tests pass**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine test`

Expected: PASS.

### Task 4: Verify repository-wide compatibility

**Files:**
- No additional changes expected

**Step 1: Run full verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: PASS across all modules.
