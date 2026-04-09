# Analysis Coordinator Parallel Engines Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Execute engines in parallel with bounded concurrency and per-engine timeouts so task tail latency is not dominated by the slowest engine.

**Architecture:** Split task execution into two layers: a task-level coordinator thread and a dedicated engine fan-out layer. Each engine run gets a child `ExecutionContext` derived from the task context, so task cancellation propagates downward while per-engine timeout stays isolated. Use `CompletableFuture` fan-out with a global semaphore bulkhead to cap concurrent engine work, and degrade timed-out/failed engines into placeholder `EngineResult`s so forum/report can still complete with partial coverage.

**Tech Stack:** Java 21, Spring Boot, `CompletableFuture`, `Semaphore`, JUnit 5

### Task 1: Lock the desired coordinator behavior with tests

**Files:**
- Modify: `bettafish-app/src/test/java/com/bettafish/app/service/AnalysisCoordinatorTest.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`

**Step 1: Write the failing tests**

- Add a coordinator test that starts multiple blocking engines and asserts they run in parallel instead of serially.
- Add a coordinator test that configures `maxConcurrentEngines=1` (or `2`) and asserts observed concurrent engine count never exceeds the bulkhead limit.
- Add a coordinator test that configures a short per-engine timeout and asserts the task still completes with a placeholder/degraded `EngineResult` for the timed-out engine instead of waiting for the full task timeout.

**Step 2: Run tests to verify they fail**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`

Expected: FAIL because `AnalysisCoordinator` still executes engines serially and has no per-engine timeout/bulkhead API.

### Task 2: Add execution settings and child execution-context support

**Files:**
- Create: `bettafish-app/src/main/java/com/bettafish/app/config/AnalysisExecutionPolicy.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/config/AnalysisExecutionConfiguration.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/engine/ExecutionContext.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/config/BettaFishProperties.java`
- Modify: `bettafish-app/src/main/resources/application.yml`

**Step 1: Write minimal implementation**

- Add execution policy fields for task timeout, per-engine timeout, and max concurrent engines.
- Expose a dedicated engine executor bean for fan-out work.
- Extend `ExecutionContext` to support a parent context so task cancellation/timeout propagates into child engine contexts, while engine-local timeout stays isolated.

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app,bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`

Expected: compile/test failure moves from missing API toward missing coordinator fan-out logic.

### Task 3: Parallelize engine fan-out with bulkhead + degraded fallback

**Files:**
- Modify: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisCoordinator.java`

**Step 1: Write minimal implementation**

- Fan out engine execution with `CompletableFuture` and wait for completion with cooperative task-cancellation polling.
- Gate engine starts with a global `Semaphore` bulkhead.
- For each engine:
  - create a child `ExecutionContext` with the per-engine timeout;
  - publish `EngineStartedEvent`;
  - run the engine on the engine executor;
  - turn engine-local timeout/failure into a placeholder `EngineResult` with status metadata, while rethrowing task-level cancellation.
- Preserve deterministic ordering of final `engineResults`.

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`

Expected: new coordinator tests PASS.

### Task 4: Verify downstream integration still holds

**Files:**
- Modify if needed: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`

**Step 1: Run cross-module regression**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app,bettafish-common,bettafish-query-engine,bettafish-media-engine,bettafish-insight-engine,bettafish-forum-engine,bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest,AnalysisControllerTest,SpringAiLlmGatewayTest,TavilySearchToolTest,HttpSentimentAnalysisClientTest,ForumCoordinatorTest,ReportAgentTest,ChapterGenerationNodeTest,KeywordOptimizerTest,InsightAgentTest,MediaAgentTest,QueryAgentTest test`

Expected: PASS with task cancellation/timeout still working and engine fan-out no longer serialized.
