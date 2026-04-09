# LlmGateway Unification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend the shared LLM gateway so every engine can use one consistent flow for prompt execution, structured parsing, validation, and fallback.

**Architecture:** Keep `SpringAiLlmGateway` as the only Spring AI adapter, then move shared concerns into the gateway contract: `callText`, validator-aware `callJson`, and deterministic fallback behavior. Existing engine code should stay thin and express only prompt construction plus domain validation.

**Tech Stack:** Java 21, Spring AI, Jackson, JUnit 5, Mockito

### Task 1: Add red tests for gateway capabilities

**Files:**
- Modify: `bettafish-common/src/test/java/com/bettafish/common/llm/SpringAiLlmGatewayTest.java`

**Step 1: Write the failing test**

Add tests for:
- plain text model calls through `callText(...)`
- validator-rejected JSON responses downgrading to fallback
- validator-rejected JSON responses throwing without fallback

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SpringAiLlmGatewayTest test`

Expected: compile/test failure because `LlmGateway` lacks `callText` and validator-aware `callJson`.

### Task 2: Extend common gateway contract and implementation

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/llm/LlmGateway.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/llm/SpringAiLlmGateway.java`

**Step 1: Write minimal implementation**

Add:
- `callText(...)`
- `Validator<T>`
- `ValidationResult`
- validator-aware `callJson(...)` overloads for `Class<T>` and `TypeReference<T>`

Make existing overloads delegate to the new validator-aware path with a no-op validator.

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SpringAiLlmGatewayTest test`

Expected: PASS

### Task 3: Migrate direct LLM caller in Insight keyword optimization

**Files:**
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/keyword/KeywordOptimizer.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/InsightAgent.java`
- Modify: `bettafish-insight-engine/src/test/java/com/bettafish/insight/InsightAgentTest.java`
- Create: `bettafish-insight-engine/src/test/java/com/bettafish/insight/keyword/KeywordOptimizerTest.java`

**Step 1: Write the failing test**

Ensure `KeywordOptimizer` reads keyword suggestions via `LlmGateway` and falls back to deterministic defaults when validation fails.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-insight-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KeywordOptimizerTest,InsightAgentTest test`

Expected: compile/test failure until production code is migrated.

**Step 3: Write minimal implementation**

Inject `LlmGateway`, call `keywordOptimizerChatClient` through the gateway, validate the returned keyword list, and keep existing deterministic fallback.

**Step 4: Run tests to verify it passes**

Run the same command and expect `PASS`.

### Task 4: Apply validators to current structured-output nodes

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumHost.java`
- Modify: `bettafish-report-engine/src/main/java/com/bettafish/report/node/ChapterGenerationNode.java`

**Step 1: Add minimal domain validators**

Validate:
- paragraph plans are non-empty and title/content are present
- forum guidance has a usable prompt addendum
- report chapter draft has a non-null block list

**Step 2: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-forum-engine,bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest,ForumCoordinatorTest,ChapterGenerationNodeTest,ReportAgentTest test`

Expected: PASS

### Task 5: Final verification

**Files:**
- No code changes

**Step 1: Run regression suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: `BUILD SUCCESS`
