# Media Insight Single-Paragraph Shared Layer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extract the shared Media/Insight single-paragraph state machine skeleton into `bettafish-common` so future workflow changes do not require duplicating node and context plumbing.

**Architecture:** Keep domain behavior in `MediaAgent` and `InsightAgent`, but move the shared workflow shell into `SingleParagraphNodeContext` and `SingleParagraphWorkflowNodes`. Preserve node names such as `PlanSearchNode` and `FinalizeReportNode` so existing runtime state and tests remain stable.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Maven

### Task 1: Extract Shared Runtime Shell

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/runtime/SingleParagraphNodeContext.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/runtime/SingleParagraphWorkflowNodes.java`

**Step 1: Define the shared context contract**

Add a shared base context that owns:
- `AnalysisRequest`
- `AgentState`
- `maxReflections`
- pending search query / reasoning
- single paragraph lookup
- default node name to workflow status mapping

**Step 2: Define reusable named workflow nodes**

Add a node factory that can create:
- `PlanSearchNode`
- `ExecuteSearchNode`
- `SummarizeFindingsNode`
- `ReflectOnGapsNode`
- `RefineSearchNode`
- `ReflectionSummaryNode`
- `FinalizeReportNode`

**Step 3: Verify compile path**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am test -DskipTests`
Expected: PASS

### Task 2: Migrate Media And Insight To The Shared Layer

**Files:**
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/node/MediaNode.java`
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/node/MediaNodeContext.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/node/InsightNode.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/node/InsightNodeContext.java`

**Step 1: Replace duplicated node classes with shared node factories**

Keep exported constants unchanged and route each node to the existing agent method.

**Step 2: Replace duplicated context fields with the shared base context**

Retain only engine-specific fields:
- `MediaAgent`
- `InsightAgent`
- `SentimentSignal`
- `latestKeywords`

**Step 3: Verify focused regressions**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest test`
Expected: PASS

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InsightAgentTest test`
Expected: PASS

### Task 3: Verify Combined Behavior

**Files:**
- Modify only files above as needed

**Step 1: Run combined focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine,bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest,InsightAgentTest test`
Expected: PASS
