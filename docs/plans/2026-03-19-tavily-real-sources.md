# Tavily Real Sources Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace placeholder Tavily search results with real HTTP-backed sources and expose those source URLs through QueryAgent state and SSE events.

**Architecture:** Keep the `TavilySearchTool` interface small, but back it with a configurable HTTP client that reads `baseUrl/apiKey/maxResults` from existing properties. After each search, `QueryAgent` should persist the real sources in paragraph state and emit a source snapshot delta so SSE clients can observe the retrieved URLs immediately after `ToolCalledEvent`.

**Tech Stack:** Java 21 `HttpClient`, Jackson, Spring Boot configuration properties, JUnit 5, MockMvc

### Task 1: Write failing tests for real source propagation

**Files:**
- Create: `bettafish-query-engine/src/test/java/com/bettafish/query/tool/TavilySearchToolTest.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`

**Step 1: Write the failing test**

Add tests that assert:
- Tavily HTTP responses map into at least 3 `SourceReference` values with non-placeholder URLs
- `QueryAgentTest` sees non-`example.com` sources and a `DeltaChunkEvent` containing those URLs after `ToolCalledEvent`
- SSE output contains both `ToolCalledEvent` and a subsequent `DeltaChunkEvent` with real source URLs

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TavilySearchToolTest,QueryAgentTest,AnalysisControllerTest test`

Expected: FAIL because `TavilySearchTool` still returns placeholder data and QueryAgent emits no search-result delta.

### Task 2: Implement Tavily HTTP client

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/tool/TavilySearchTool.java`

**Step 1: Write minimal implementation**

Implement:
- injected `baseUrl/apiKey/maxResults`
- HTTP POST to Tavily search endpoint
- JSON mapping of `results[].title/url/content`
- empty/error handling without `example.com` placeholders

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TavilySearchToolTest test`

Expected: PASS

### Task 3: Publish search source snapshots

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`

**Step 1: Write minimal implementation**

After each tool execution:
- append sources to paragraph state as before
- publish a `DeltaChunkEvent` with titles and URLs of retrieved sources

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest,AnalysisControllerTest test`

Expected: PASS

### Task 4: Final verification

**Files:**
- No code changes

**Step 1: Run regression suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: `BUILD SUCCESS`
