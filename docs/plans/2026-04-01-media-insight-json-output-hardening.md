# Media Insight JSON Output Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make MediaEngine and InsightEngine emit validated JSON for paragraph summary and final conclusion stages, and have ReportEngine consume the structured result as stronger chapter input.

**Architecture:** Reuse the existing `LlmGateway.callJson(...)` + validator + fallback pattern already used by QueryEngine. Store validated structured narrative fields on `ParagraphState`, project the final conclusion and key points into `EngineResult`, and let `ReportAgent` prefer those structured fields when building chapter material.

**Tech Stack:** Java 21, Spring, Jackson records/annotations, JUnit 5, existing state-machine workflow.

### Task 1: Add failing tests for structured Media outputs

**Files:**
- Modify: `bettafish-media-engine/src/test/java/com/bettafish/media/MediaAgentTest.java`

**Step 1: Write the failing test**

Add assertions that MediaAgent:
- uses JSON responses for first summary, reflection summary, and final conclusion
- persists validated key points / final conclusion into the result
- exposes structured metadata for downstream report generation

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest test`

Expected: FAIL because MediaAgent still uses `callText(...)` for summary/reflection and does not emit structured final conclusion fields.

### Task 2: Add failing tests for structured Insight outputs

**Files:**
- Modify: `bettafish-insight-engine/src/test/java/com/bettafish/insight/InsightAgentTest.java`

**Step 1: Write the failing test**

Add assertions that InsightAgent:
- uses JSON responses for first summary, reflection summary, and final conclusion
- projects validated final conclusion and key points into the result
- keeps fallback behavior when JSON validation fails

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InsightAgentTest test`

Expected: FAIL because InsightAgent still uses free-form text for summary/reflection/finalization.

### Task 3: Add failing test for Report structured chapter input

**Files:**
- Modify: `bettafish-report-engine/src/test/java/com/bettafish/report/ReportAgentTest.java`

**Step 1: Write the failing test**

Add assertions that `ReportAgent` builds chapter source material from structured engine fields when available, not only `headline | summary | key points`.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportAgentTest test`

Expected: FAIL because `ReportAgent` still builds plain text source material without structured conclusion/gap fields.

### Task 4: Implement shared structured narrative state

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/model/ParagraphState.java`

**Step 1: Write minimal implementation**

Add typed fields for:
- current key points
- current evidence gaps
- current final conclusion

Keep `currentDraft` for backward compatibility with existing QueryEngine flow.

**Step 2: Run affected tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-media-engine,bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest,InsightAgentTest test`

Expected: still failing until agents are updated.

### Task 5: Convert MediaAgent to JSON summary / reflection / final conclusion

**Files:**
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/MediaAgent.java`
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/prompt/MediaPrompts.java`

**Step 1: Write minimal implementation**

Introduce structured response records and validators for:
- summary stage
- reflection summary stage
- final conclusion stage

Persist structured fields on `ParagraphState`, publish human-readable summary strings to delta/speech events, and write final structured conclusion into `AgentState.finalReport` and `EngineResult`.

**Step 2: Run tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-media-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest test`

Expected: PASS.

### Task 6: Convert InsightAgent to JSON summary / reflection / final conclusion

**Files:**
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/InsightAgent.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/prompt/InsightPrompts.java`

**Step 1: Write minimal implementation**

Mirror the MediaAgent structured-output pattern while preserving sentiment-specific context, optimized keywords, and guidance-aware fallback behavior.

**Step 2: Run tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InsightAgentTest test`

Expected: PASS.

### Task 7: Feed structured engine outputs into ReportAgent

**Files:**
- Modify: `bettafish-report-engine/src/main/java/com/bettafish/report/ReportAgent.java`

**Step 1: Write minimal implementation**

When engine metadata indicates structured narrative output is present, build chapter source material from:
- headline
- summary draft
- final conclusion
- key points
- evidence gaps / follow-up questions when available

Fall back to the legacy plain-text material when structured fields are absent.

**Step 2: Run tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportAgentTest test`

Expected: PASS.

### Task 8: Run combined regression

**Files:**
- Modify: `bettafish-media-engine/src/test/java/com/bettafish/media/MediaAgentTest.java`
- Modify: `bettafish-insight-engine/src/test/java/com/bettafish/insight/InsightAgentTest.java`
- Modify: `bettafish-report-engine/src/test/java/com/bettafish/report/ReportAgentTest.java`

**Step 1: Run targeted suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-media-engine,bettafish-insight-engine,bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MediaAgentTest,InsightAgentTest,ReportAgentTest,ChapterGenerationNodeTest test`

Expected: PASS.

**Step 2: Run cross-module guardrail suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app,bettafish-common,bettafish-query-engine,bettafish-media-engine,bettafish-insight-engine,bettafish-forum-engine,bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest,AnalysisControllerTest,SpringAiLlmGatewayTest,TavilySearchToolTest,HttpSentimentAnalysisClientTest,ForumCoordinatorTest,ReportAgentTest,ChapterGenerationNodeTest,KeywordOptimizerTest,InsightAgentTest,MediaAgentTest,QueryAgentTest test`

Expected: PASS.
