# Runtime Node Runner Alignment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade `bettafish-common` runtime to reusable `Node/NodeContext/StateMachineRunner` primitives and align `QueryEngine` to the fixed BettaFish skeleton: `PlanParagraphsNode` → per paragraph `FirstSearchDecisionNode → ToolExecuteNode → FirstSummaryNode → ReflectionDecisionNode → (loop N times) → ReflectionSummaryNode` → `FormatReportNode`.

**Architecture:** Replace the current enum-bound runtime with object-based nodes so each node is a reusable executable unit with an explicit name. Extend `NodeContext` into a small dependency/state carrier that can expose shared services such as `LlmGateway`, tools, `AnalysisEventPublisher`, and typed attributes. Migrate `QueryAgent` from enum constants to explicit node objects while preserving current observable behavior and `AgentState`/`ParagraphState` updates.

**Tech Stack:** Java 21, Spring Boot, Jackson, JUnit 5

### Task 1: Replace Common Runtime With Reusable Node Objects

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/runtime/Node.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/runtime/NodeContext.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/runtime/StateMachineRunner.java`
- Modify: `bettafish-common/src/test/java/com/bettafish/common/runtime/StateMachineRunnerTest.java`

**Step 1: Write/update the failing test**

Update runtime tests to expect:
- `Node` is an object with a stable `name()`
- `StateMachineRunner` executes object nodes in sequence
- `NodeContext` records node names and still publishes `NodeStartedEvent`
- `NodeContext` can store and retrieve typed services/attributes

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StateMachineRunnerTest test`
Expected: FAIL because runtime is still enum-shaped.

**Step 3: Write minimal implementation**

Implement object-based `Node<C>` plus a `NodeContext` that stores:
- task id
- engine name
- event publisher
- current node name
- node history
- typed service registry
- typed attribute registry

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StateMachineRunnerTest test`
Expected: PASS

### Task 2: Write Failing Query Skeleton Test

**Files:**
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`

**Step 1: Write the failing test**

Update `QueryAgentTest` to assert the runtime visits the fixed node-name skeleton:
- `PlanParagraphsNode`
- `FirstSearchDecisionNode`
- `ToolExecuteNode`
- `FirstSummaryNode`
- `ReflectionDecisionNode`
- `ReflectionSummaryNode`
- `FormatReportNode`

Also assert the loop repeats for each paragraph and reflection cycle while preserving `AgentState` / `ParagraphState`.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest test`
Expected: FAIL because Query still uses old enum node names and transitions.

### Task 3: Migrate Query To Fixed Node Objects

**Files:**
- Delete/replace: `bettafish-query-engine/src/main/java/com/bettafish/query/node/QueryNode.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/node/QueryNodeContext.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Create if needed: `bettafish-query-engine/src/main/java/com/bettafish/query/node/*.java`

**Step 1: Write minimal implementation**

Introduce explicit node objects:
- `PlanParagraphsNode`
- `FirstSearchDecisionNode`
- `ToolExecuteNode`
- `FirstSummaryNode`
- `ReflectionDecisionNode`
- `ReflectionSummaryNode`
- `FormatReportNode`

Use `QueryNodeContext` fields to decide whether `ToolExecuteNode` is handling first search or refinement, and whether `ReflectionSummaryNode` should loop back into `ReflectionDecisionNode` or advance to the next paragraph.

**Step 2: Run query test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest test`
Expected: PASS

### Task 4: Verify Runtime + Query Integration

**Files:**
- Modify only files above as needed

**Step 1: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StateMachineRunnerTest,QueryAgentTest test`
Expected: PASS

**Step 2: Run full test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`
Expected: PASS
