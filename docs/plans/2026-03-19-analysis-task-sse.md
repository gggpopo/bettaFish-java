# Analysis Task SSE Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add `GET /api/analysis/{taskId}/events` as a task-scoped SSE stream so clients can replay existing analysis events and receive new progress events in real time.

**Architecture:** Extend the in-memory event bus with task-scoped subscriptions and explicit unsubscribe handles so an SSE connection can atomically replay history and continue receiving live events without leaks. Expose the stream from `AnalysisController` using `SseEmitter`, serialize each `AnalysisEvent` with an SSE event name equal to the event kind, and close the stream when the task emits a terminal event.

**Tech Stack:** Java 21, Spring Boot MVC, SseEmitter, Jackson, JUnit 5, MockMvc

### Task 1: Add Task-Scoped Replayable Subscriptions

**Files:**
- Modify: `bettafish-app/src/main/java/com/bettafish/app/event/EventBus.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/event/InMemoryEventBus.java`
- Test: `bettafish-app/src/test/java/com/bettafish/app/event/InMemoryEventBusTest.java`

**Step 1: Write the failing test**

Add a test that subscribes to one task with replay enabled, verifies historical events are replayed, verifies future events for the same task are pushed, ignores other tasks, and confirms unsubscribe stops delivery.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InMemoryEventBusTest test`
Expected: FAIL because task-scoped subscriptions and unsubscribe handles do not exist.

**Step 3: Write minimal implementation**

Add an event subscription handle and implement synchronized task-scoped replay/subscribe behavior in the in-memory event bus.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InMemoryEventBusTest test`
Expected: PASS

### Task 2: Add Terminal Failure Event

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisFailedEvent.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisCoordinator.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/service/AnalysisCoordinatorTest.java`

**Step 1: Write the failing test**

Add a test that forces analysis failure and asserts an `AnalysisFailedEvent` is published so SSE clients can terminate on errors.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`
Expected: FAIL because no failure terminal event exists.

**Step 3: Write minimal implementation**

Create `AnalysisFailedEvent` and publish it in the coordinator catch path after persisting the failed snapshot.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`
Expected: PASS

### Task 3: Add SSE Endpoint

**Files:**
- Modify: `bettafish-app/src/main/java/com/bettafish/app/controller/AnalysisController.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`

**Step 1: Write the failing test**

Add one test that creates a task, calls `GET /api/analysis/{taskId}/events`, and asserts the response is `text/event-stream` containing replayed `EngineStartedEvent` and `AnalysisCompleteEvent`. Add one test for unknown task returning 404.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisControllerTest test`
Expected: FAIL because the SSE endpoint does not exist.

**Step 3: Write minimal implementation**

Inject the event bus and `ObjectMapper` into the controller, open a `SseEmitter`, subscribe with replay enabled, send each event using SSE `event:` names plus JSON payloads, and complete the stream on `AnalysisCompleteEvent` or `AnalysisFailedEvent`.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisControllerTest test`
Expected: PASS

### Task 4: Verify Build

**Files:**
- Modify only files above as needed

**Step 1: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InMemoryEventBusTest,AnalysisCoordinatorTest,AnalysisControllerTest test`
Expected: PASS

**Step 2: Run full test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`
Expected: PASS
