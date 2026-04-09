# LlmGateway JSON Governance Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Introduce a shared `LlmGateway.callJson(...)` in `bettafish-common` so all node-level LLM calls go through one Spring AI entry point with strict JSON extraction and parsing rules.

**Architecture:** Add a common `LlmGateway` abstraction backed by a Spring AI implementation that routes by named `ChatClient`, appends JSON-only instructions, sanitizes fenced responses, and deserializes into typed DTOs. Refactor `QueryAgent` node methods to call the gateway for planning, summary, reflection decision, and final report assembly instead of embedding deterministic text generation.

**Tech Stack:** Java 21, Spring AI `ChatClient`, Jackson `ObjectMapper`, JUnit 5, Mockito, Maven.

### Task 1: Define the gateway and Query integration in tests

**Files:**
- Create: `bettafish-common/src/test/java/com/bettafish/common/llm/SpringAiLlmGatewayTest.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`

**Step 1: Write the failing test**

Add `SpringAiLlmGatewayTest` to verify:
- `callJson(...)` can parse JSON wrapped in markdown fences
- `callJson(...)` throws when the target client is missing or the content is not valid JSON

Update `QueryAgentTest` to verify:
- `QueryAgent` gets paragraph plans, summaries, reflection decisions, and final report from a fake `LlmGateway`
- gateway calls use the `queryChatClient` logical client
- paragraph titles/final report reflect gateway output rather than hard-coded local text

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine test`

Expected: FAIL because `LlmGateway` and Query integration do not exist yet.

### Task 2: Implement the shared LLM gateway

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/llm/LlmGateway.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/llm/SpringAiLlmGateway.java`

**Step 1: Write minimal implementation**

Implement:
- overloads for `callJson(...)` with `Class<T>` and `TypeReference<T>`
- named client lookup from Spring-managed `ChatClient` beans
- prompt normalization that appends strict JSON output instructions
- raw-content cleanup that strips markdown fences and extracts the JSON body
- strict Jackson deserialization with meaningful exceptions

**Step 2: Run test to verify gateway tests pass**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common test`

Expected: PASS for the new gateway tests and existing common tests.

### Task 3: Refactor Query node LLM steps to use the gateway

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/prompt/QueryPrompts.java`

**Step 1: Write minimal implementation**

Refactor `QueryAgent` so these stages call `llmGateway.callJson(...)`:
- report structure planning
- first summary
- reflection decision
- reflection summary
- final report assembly

Use small typed DTO records for structured outputs and keep search execution in `TavilySearchTool`.

**Step 2: Run test to verify query tests pass**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine test`

Expected: PASS for the updated `QueryAgentTest`.

### Task 4: Verify repository-wide compatibility

**Files:**
- No additional changes expected

**Step 1: Run focused verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine test`

Expected: PASS.

**Step 2: Run full verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: PASS with no regressions in other modules.
