# Media Insight Reflection Decision Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current guidance-exists heuristic in `MediaAgent` and `InsightAgent` with structured `callJson(...)` reflection decisions so refinement search is explicitly model-driven.

**Architecture:** Keep the existing state-machine nodes and only upgrade `ReflectOnGapsNode` behavior. Add engine-specific reflection decision prompts and DTOs, make the node call `LlmGateway.callJson(...)`, validate the response, and fall back to a deterministic default when the model fails. Tests should prove both branches: refine when JSON says refine, and stop when JSON says stop.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Maven, shared BettaFish runtime/LLM gateway

### Task 1: Write Failing Media Reflection Decision Test

**Files:**
- Modify: `bettafish-media-engine/src/test/java/com/bettafish/media/MediaAgentTest.java`

**Step 1: Write the failing test**

Add a test that:
- provides `ForumGuidance`
- makes the gateway return a structured reflection decision with `shouldRefine=false`
- expects the media workflow to stop at one search round even though guidance exists

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest test`
Expected: FAIL because the workflow still refines whenever guidance exists.

### Task 2: Implement Media Structured Reflection Decision

**Files:**
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/MediaAgent.java`
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/prompt/MediaPrompts.java`

**Step 1: Write minimal implementation**

Add:
- `REFLECTION_DECISION_SYSTEM`
- `buildReflectionDecisionUserPrompt(...)`
- `ReflectionDecisionResponse`
- validator + default fallback

Make `reflectOnGaps(...)` use `callJson(...)` and only refine when the JSON decision says to refine.

**Step 2: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest test`
Expected: PASS

### Task 3: Write Failing Insight Reflection Decision Test

**Files:**
- Modify: `bettafish-insight-engine/src/test/java/com/bettafish/insight/InsightAgentTest.java`

**Step 1: Write the failing test**

Add a test that:
- provides `ForumGuidance`
- makes the gateway return `shouldRefine=false`
- expects the insight workflow to stop at one DB search round even though guidance exists

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InsightAgentTest test`
Expected: FAIL because the workflow still refines whenever guidance exists.

### Task 4: Implement Insight Structured Reflection Decision

**Files:**
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/InsightAgent.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/prompt/InsightPrompts.java`

**Step 1: Write minimal implementation**

Add:
- `REFLECTION_DECISION_SYSTEM`
- `buildReflectionDecisionUserPrompt(...)`
- `ReflectionDecisionResponse`
- validator + default fallback

Make `reflectOnGaps(...)` use `callJson(...)` and only refine when the JSON decision says to refine.

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
