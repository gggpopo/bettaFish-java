# Analysis Task Cancel/Timeout Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make each analysis task run independently, support cancellation and timeout, and publish terminal events that close SSE streams.

**Architecture:** Replace the current synchronous `Runnable::run` path with real asynchronous task execution managed by `AnalysisCoordinator`. Each task gets an `ExecutionContext` carrying cancellation/timeout state; the coordinator owns lifecycle, persists snapshots, and emits terminal events. Engines opt into cooperative cancellation through a new overload, starting with `QueryAgent`.

**Tech Stack:** Java 21 concurrency primitives, Spring Boot configuration, existing EventBus/SSE stack, JUnit 5

### Task 1: Write failing tests for async lifecycle

**Files:**
- Modify: `bettafish-app/src/test/java/com/bettafish/app/service/AnalysisCoordinatorTest.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`

**Step 1: Write the failing test**

Add tests that assert:
- `startAnalysis(...)` returns a `RUNNING` snapshot before work is drained
- `cancelAnalysis(taskId)` drives the task into a terminal cancel state and publishes a cancel terminal event
- SSE closes after replaying the cancel terminal event

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest,AnalysisControllerTest test`

Expected: FAIL because coordinator still executes synchronously and has no cancel endpoint/terminal cancel event.

### Task 2: Add execution context and terminal task states

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/engine/ExecutionContext.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/engine/ExecutionCancelledException.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisCancelledEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisTimedOutEvent.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/api/AnalysisStatus.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/engine/AnalysisEngine.java`

**Step 1: Write minimal implementation**

Add:
- `CANCELLED` and `TIMED_OUT` statuses
- execution context with user cancel / timeout flags
- new engine overload carrying execution context
- terminal event types for cancel and timeout

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am test`

Expected: PASS

### Task 3: Refactor coordinator to true async execution

**Files:**
- Modify: `bettafish-app/src/main/java/com/bettafish/app/config/AnalysisExecutionConfiguration.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisCoordinator.java`

**Step 1: Write minimal implementation**

Implement:
- real task executor and timeout scheduler beans
- running-task registry inside coordinator
- async `startAnalysis(...)`
- `cancelAnalysis(taskId)`
- timeout-triggered terminal state
- publish terminal events and persist terminal snapshots once

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`

Expected: PASS

### Task 4: Make QueryAgent cancellation-aware and expose cancel endpoint

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/runtime/StateMachineRunner.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/controller/AnalysisController.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`

**Step 1: Write minimal implementation**

Add:
- cooperative cancel checks in runner / QueryAgent
- `POST /api/analysis/{taskId}/cancel`
- SSE terminal detection for cancel and timeout events

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest,AnalysisControllerTest,QueryAgentTest test`

Expected: PASS

### Task 5: Final verification

**Files:**
- No code changes

**Step 1: Run regression suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: `BUILD SUCCESS`
