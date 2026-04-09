# ExecutionContext Deep Cancel Propagation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make task timeout/cancellation exit within a bounded time even when the task is blocked inside LLM or tool HTTP calls.

**Architecture:** Keep `AnalysisCoordinator` as the owner of task lifecycle, but expose the active `ExecutionContext` to deep call stacks through a task-thread scope. Add a reusable blocking-call guard that runs blocking work on cancellable worker threads, polls `ExecutionContext` state/deadline, interrupts the worker on cancel/timeout, and lets callers propagate `ExecutionCancelledException` instead of silently falling back.

**Tech Stack:** Java 21, Spring AI 1.0.1, JDK `HttpClient`, Spring `RestClient`, JUnit 5, Mockito

### Task 1: Reproduce deep-call cancellation failures

**Files:**
- Modify: `bettafish-common/src/test/java/com/bettafish/common/llm/SpringAiLlmGatewayTest.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/tool/TavilySearchToolTest.java`

**Step 1: Write the failing tests**

- Add a `SpringAiLlmGateway` test where `ChatClient.call().content()` blocks until interrupted, then cancel the active `ExecutionContext` and assert the gateway exits with `ExecutionCancelledException` instead of hanging or falling back.
- Add a `TavilySearchTool` test with a blocking `HttpClient` fake, then cancel the active `ExecutionContext` and assert the tool exits promptly with `ExecutionCancelledException`.

**Step 2: Run tests to verify they fail**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SpringAiLlmGatewayTest,TavilySearchToolTest test`

Expected: new cancellation tests fail because current LLM/tool code does not observe deep cancellation.

### Task 2: Add shared execution-context deep-call primitives

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/engine/ExecutionContextHolder.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/engine/BlockingCallGuard.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/engine/ExecutionContext.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/util/RetryHelper.java`

**Step 1: Write minimal implementation**

- Add a thread-scoped holder so work running inside one analysis task can access the active `ExecutionContext` without pushing method parameters through every layer immediately.
- Add a blocking-call guard that:
  - checks cancellation before starting;
  - computes remaining time from the task deadline;
  - waits in short slices;
  - interrupts the worker thread on cancel/timeout;
  - rethrows `ExecutionCancelledException` unchanged.
- Extend `ExecutionContext` with deadline-aware helpers (`remainingTime`, deadline-expiry promotion to timeout).
- Make `RetryHelper` stop retrying on cancellation and use cancellation-aware sleep.

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SpringAiLlmGatewayTest test`

Expected: `SpringAiLlmGatewayTest` passes once the shared primitives exist and are wired.

### Task 3: Wire LLM and tool integrations to the shared guard

**Files:**
- Modify: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisCoordinator.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/llm/SpringAiLlmGateway.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/tool/TavilySearchTool.java`
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/tool/BochaSearchTool.java`
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/tool/AnspireSearchTool.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/tool/MediaCrawlerDbTool.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/HttpSentimentAnalysisClient.java`

**Step 1: Write minimal implementation**

- Scope the active `ExecutionContext` around `AnalysisCoordinator.executeTask(...)`.
- Guard Spring AI `ChatClient` requests with the blocking-call guard so cancel/timeout interrupts blocked LLM calls and never downgrades to fallback after cancellation.
- Guard Tavily HTTP requests the same way, clamp request timeout to the task’s remaining time, and preserve existing non-cancellation fallback behavior.
- For current stub tools/clients, add explicit `throwIfCancellationRequested()` checks so future real integrations inherit the same contract.

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine,bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SpringAiLlmGatewayTest,TavilySearchToolTest,HttpSentimentAnalysisClientTest test`

Expected: all targeted tests pass.

### Task 4: Verify end-to-end task termination behavior

**Files:**
- Modify if needed: `bettafish-app/src/test/java/com/bettafish/app/service/AnalysisCoordinatorTest.java`

**Step 1: Add or adjust regression coverage**

- If current coordinator tests do not cover deep blocking boundaries, add a regression that drives cancellation/timeout through a blocking gateway/tool-backed engine and verifies the task reaches `CANCELLED` or `TIMED_OUT`.

**Step 2: Run verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app,bettafish-common,bettafish-query-engine,bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest,SpringAiLlmGatewayTest,TavilySearchToolTest,HttpSentimentAnalysisClientTest test`

Expected: all targeted tests pass and no task remains stuck after cancellation/timeout.
