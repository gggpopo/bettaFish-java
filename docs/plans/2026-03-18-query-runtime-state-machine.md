# Query Runtime State Machine Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a shared runtime package in `bettafish-common` so enum-based engine nodes can execute through one state-machine runner, then make `QueryAgent` run the full in-memory plan/search/summarize/reflect/finalize loop while persisting intermediate state into `AgentState` and `ParagraphState`.

**Architecture:** Promote `QueryNode` from a passive enum to an executable node enum that implements a shared `Node` contract. Add a reusable `StateMachineRunner` and `NodeContext` in `bettafish-common`, expand the common state models to hold paragraph drafts and search history, and drive `QueryAgent` through the shared runner with deterministic in-memory planning and reflection behavior.

**Tech Stack:** Java 21, Spring Boot 3.4, JUnit 5, Maven, Spring component wiring.

### Task 1: Define the expected runtime behavior in tests

**Files:**
- Create: `bettafish-common/src/test/java/com/bettafish/common/runtime/StateMachineRunnerTest.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`

**Step 1: Write the failing test**

Add a `StateMachineRunnerTest` that proves:
- an enum node can execute and transition through the shared runner
- the context records the current node sequence

Update `QueryAgentTest` so it expects:
- `QueryAgent` returns a `QUERY` result assembled from multiple paragraph sections
- `metadata` includes state-machine details such as node mode / reflection count / paragraph count
- an exposed runtime state contains paragraph search history, paragraph drafts, completion flags, and a final report

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine test`

Expected: FAIL because `bettafish.common.runtime` and the richer query workflow do not exist yet.

### Task 2: Expand shared runtime and state models

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/runtime/Node.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/runtime/NodeContext.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/runtime/StateMachineRunner.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/model/AgentState.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/model/ParagraphState.java`

**Step 1: Write minimal implementation**

Implement:
- a shared `Node` contract that enum nodes can implement directly
- a mutable `NodeContext` with node tracking and typed attributes
- a `StateMachineRunner` that loops from a start node until `null`
- richer mutable agent/paragraph state with search history, drafts, final report, current node, and reflection counters

**Step 2: Run test to verify the runtime test passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common test`

Expected: PASS for `StateMachineRunnerTest`.

### Task 3: Make QueryEngine use the shared runner

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/node/QueryNode.java`
- Create: `bettafish-query-engine/src/main/java/com/bettafish/query/node/QueryNodeContext.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/prompt/QueryPrompts.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/tool/TavilySearchTool.java`

**Step 1: Write minimal implementation**

Implement a deterministic in-memory loop:
- `PLAN_SEARCH` creates ordered paragraph plans from the request query
- `EXECUTE_SEARCH` runs the first paragraph search with Tavily tool
- `SUMMARIZE_FINDINGS` creates or extends paragraph drafts from latest search results
- `REFLECT_ON_GAPS` decides whether another search round is needed
- `REFINE_SEARCH` runs reflection searches
- `FINALIZE_REPORT` concatenates completed paragraph drafts into the final report

Persist state on every transition:
- `AgentState.status`, `AgentState.round`, `AgentState.finalReport`, `AgentState.paragraphs`
- `ParagraphState.currentDraft`, `ParagraphState.completed`, `ParagraphState.reflectionRoundsCompleted`, `ParagraphState.searchHistory`

**Step 2: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine test`

Expected: PASS for the updated `QueryAgentTest`.

### Task 4: Verify cross-module behavior

**Files:**
- No additional code changes expected

**Step 1: Run focused verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-query-engine test`

Expected: PASS with the new runtime and query flow covered.

**Step 2: Run full verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: PASS so the shared-model changes do not break the other renamed skeleton modules.
