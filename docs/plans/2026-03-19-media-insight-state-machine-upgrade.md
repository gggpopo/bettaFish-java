# Media Insight State Machine Upgrade Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade `MediaAgent` and `InsightAgent` from one-shot summarizers into small state-machine workflows so `ForumGuidance` can influence the next retrieval round instead of only the current summary prompt.

**Architecture:** Reuse the shared `Node/NodeContext/StateMachineRunner` runtime already used by `QueryAgent`. Give each engine a single-paragraph workflow with explicit nodes for planning, tool execution, first summary, reflection decision, refinement search, reflection summary, and finalization. Persist intermediate search history into `AgentState` / `ParagraphState`, publish node/tool/delta/speech events, and derive reflection search inputs from the latest `ForumGuidance`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Maven, shared BettaFish runtime/event model

### Task 1: Write Failing Media Upgrade Test

**Files:**
- Modify: `bettafish-media-engine/src/test/java/com/bettafish/media/MediaAgentTest.java`

**Step 1: Write the failing test**

Add a test that expects:
- `MediaAgent` to expose a workflow state with paragraph search history
- two tool executions when guidance triggers one reflection round
- the second search query to include guidance-derived terms
- summary/reflection prompts to include `ForumGuidance`

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest test`
Expected: FAIL because `MediaAgent` is still one-shot and has no workflow state / reflection search.

### Task 2: Implement Media Runtime Workflow

**Files:**
- Delete/replace: `bettafish-media-engine/src/main/java/com/bettafish/media/node/MediaNode.java`
- Create: `bettafish-media-engine/src/main/java/com/bettafish/media/node/MediaNodeContext.java`
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/MediaAgent.java`
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/prompt/MediaPrompts.java`

**Step 1: Write minimal implementation**

Implement nodes:
- `PlanSearchNode`
- `ExecuteSearchNode`
- `SummarizeFindingsNode`
- `ReflectOnGapsNode`
- `RefineSearchNode`
- `ReflectionSummaryNode`
- `FinalizeReportNode`

Persist:
- `AgentState.currentNode`, `AgentState.status`, `AgentState.finalReport`
- one `ParagraphState` with `searchHistory`, `currentDraft`, `completed`, `forumGuidance*`

**Step 2: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest test`
Expected: PASS

### Task 3: Write Failing Insight Upgrade Test

**Files:**
- Modify: `bettafish-insight-engine/src/test/java/com/bettafish/insight/InsightAgentTest.java`

**Step 1: Write the failing test**

Add a test that expects:
- `InsightAgent` to expose workflow state with search history
- one reflection round when guidance exists
- the refined retrieval input or optimized keywords to include guidance-derived terms
- summary/reflection prompts to include `ForumGuidance`

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InsightAgentTest test`
Expected: FAIL because `InsightAgent` is still one-shot and does not refine retrieval.

### Task 4: Implement Insight Runtime Workflow

**Files:**
- Delete/replace: `bettafish-insight-engine/src/main/java/com/bettafish/insight/node/InsightNode.java`
- Create: `bettafish-insight-engine/src/main/java/com/bettafish/insight/node/InsightNodeContext.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/InsightAgent.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/prompt/InsightPrompts.java`

**Step 1: Write minimal implementation**

Implement a single-paragraph insight workflow with:
- keyword optimization
- DB search
- first summary
- one guidance-driven refinement decision
- refined keyword optimization + DB search
- reflection summary and finalization

Publish `ToolCalledEvent`, `DeltaChunkEvent`, and `AgentSpeechEvent` during the flow.

**Step 2: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InsightAgentTest test`
Expected: PASS

### Task 5: Verify Build

**Files:**
- Modify only files above as needed

**Step 1: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine,bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest,InsightAgentTest test`
Expected: PASS

**Step 2: Run full test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`
Expected: PASS
