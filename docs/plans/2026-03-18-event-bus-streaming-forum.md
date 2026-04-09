# Event Bus Streaming Forum Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Introduce observable, event-driven analysis orchestration so engines, node execution, tool calls, forum discussion, and final completion can be streamed and replayed.

**Architecture:** Define a shared analysis event contract in `bettafish-common`, including publish/subscribe hooks that engines can use without depending on the app module. Implement an in-memory event bus in `bettafish-app`, make `AnalysisCoordinator` publish orchestration events around engine/forum/report execution, and forward task-scoped events to WebSocket topics. Extend Query and Forum to publish node/tool/delta/forum events through the shared publisher.

**Tech Stack:** Java 21, Spring Boot, Spring WebSocket/STOMP, JUnit 5

### Task 1: Define Shared Event Contract

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisEventPublisher.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisEventSubscriber.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/EngineStartedEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/NodeStartedEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/ToolCalledEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/DeltaChunkEvent.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/event/AgentSpeechEvent.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/event/HostCommentEvent.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisCompleteEvent.java`
- Test: `bettafish-common/src/test/java/com/bettafish/common/runtime/StateMachineRunnerTest.java`

**Step 1: Write the failing test**

Add assertions that entering a node can publish a `NodeStartedEvent` through the shared publisher.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -Dtest=StateMachineRunnerTest test`
Expected: FAIL because `AnalysisEventPublisher` / `NodeStartedEvent` do not exist yet.

**Step 3: Write minimal implementation**

Add the event contract, make runtime contexts optionally expose a publisher, and let `NodeContext.onEnterNode` publish node-enter events.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -Dtest=StateMachineRunnerTest test`
Expected: PASS

### Task 2: Add In-Memory Event Bus and WebSocket Relay

**Files:**
- Create: `bettafish-app/src/main/java/com/bettafish/app/event/EventBus.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/event/InMemoryEventBus.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/event/AnalysisEventStreamBridge.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/config/WebSocketConfiguration.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/controller/WebSocketController.java`
- Test: `bettafish-app/src/test/java/com/bettafish/app/event/InMemoryEventBusTest.java`
- Test: `bettafish-app/src/test/java/com/bettafish/app/event/AnalysisEventStreamBridgeTest.java`

**Step 1: Write the failing tests**

Add one test for task-scoped publish/subscribe/history replay, and one test for relaying published events to the correct WebSocket topic.

**Step 2: Run tests to verify they fail**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -Dtest=InMemoryEventBusTest,AnalysisEventStreamBridgeTest test`
Expected: FAIL because the event bus and relay do not exist.

**Step 3: Write minimal implementation**

Implement an in-memory event bus with `publish`, `subscribeAll`, and `history(taskId)`. Relay every event to `/topic/analysis.{taskId}` through `SimpMessagingTemplate`.

**Step 4: Run tests to verify they pass**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -Dtest=InMemoryEventBusTest,AnalysisEventStreamBridgeTest test`
Expected: PASS

### Task 3: Publish Coordinator and Engine Events

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/engine/AnalysisEngine.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/engine/ForumCoordinator.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/engine/ReportGenerator.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisCoordinator.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/service/AnalysisCoordinatorTest.java`

**Step 1: Write the failing test**

Add assertions that coordinator emits ordered events for engine start, node execution passthrough, forum activity, report delta, and final completion.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -Dtest=AnalysisCoordinatorTest test`
Expected: FAIL because the coordinator does not publish events yet.

**Step 3: Write minimal implementation**

Inject the event bus into `AnalysisCoordinator`, add default observable overloads to the engine/forum/report interfaces, and publish final completion/failure events.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -Dtest=AnalysisCoordinatorTest test`
Expected: PASS

### Task 4: Publish Query Node, Tool, and Delta Events

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/node/QueryNodeContext.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`

**Step 1: Write the failing test**

Add assertions that running the query workflow emits `NodeStartedEvent`, `ToolCalledEvent`, and `DeltaChunkEvent` while preserving `AgentState` / `ParagraphState`.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine -Dtest=QueryAgentTest test`
Expected: FAIL because the workflow does not publish those events.

**Step 3: Write minimal implementation**

Thread the shared publisher into the query node context and publish:
- node start on every transition
- tool call before Tavily searches
- delta chunks after paragraph summaries, reflection summaries, and final report generation

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine -Dtest=QueryAgentTest test`
Expected: PASS

### Task 5: Publish Forum Collaboration Events

**Files:**
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumCoordinator.java`
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumHost.java`
- Modify: `bettafish-forum-engine/src/test/java/com/bettafish/forum/ForumCoordinatorTest.java`

**Step 1: Write the failing test**

Add assertions that each engine viewpoint emits an `AgentSpeechEvent` and the moderator emits a `HostCommentEvent`.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-forum-engine -Dtest=ForumCoordinatorTest test`
Expected: FAIL because no forum events are published.

**Step 3: Write minimal implementation**

Override the observable `coordinate(..., publisher)` path and publish one speech event per engine plus one host comment.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-forum-engine -Dtest=ForumCoordinatorTest test`
Expected: PASS

### Task 6: Verify Whole Build

**Files:**
- Modify as needed from previous tasks only

**Step 1: Run focused module tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine,bettafish-forum-engine,bettafish-app test`
Expected: PASS

**Step 2: Run full test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`
Expected: PASS
