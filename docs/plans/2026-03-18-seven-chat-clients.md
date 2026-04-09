# Seven Chat Clients Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete `LlmAutoConfiguration` so all seven configured LLM roles create dedicated `ChatModel` and `ChatClient` beans, and wire module constructors to request the correct qualified client instead of implicitly sharing the query client.

**Architecture:** Extend the common auto-configuration with four missing role-specific bean pairs: `report`, `forumHost`, `keywordOptimizer`, and `mindspider`. Then update the module services that represent those roles to accept the dedicated `ChatClient` through `@Qualifier`, keeping behavior stable while making the dependency graph explicit and ready for later real LLM usage.

**Tech Stack:** Java 21, Spring Boot 3.4, Spring AI `ChatClient`, JUnit 5, `ApplicationContextRunner`, Maven.

### Task 1: Lock expected bean coverage and injection points in tests

**Files:**
- Modify: `bettafish-common/src/test/java/com/bettafish/common/config/LlmAutoConfigurationTest.java`
- Modify: `bettafish-report-engine/src/test/java/com/bettafish/report/ReportAgentTest.java`
- Modify: `bettafish-forum-engine/src/test/java/com/bettafish/forum/ForumCoordinatorTest.java`
- Modify: `bettafish-mind-spider/src/test/java/com/bettafish/spider/CrawlerServiceTest.java`
- Modify: `bettafish-insight-engine/src/test/java/com/bettafish/insight/InsightAgentTest.java`

**Step 1: Write the failing test**

Add assertions that:
- all 7 chat client/model bean pairs exist when enabled
- `ReportAgent`, `ForumHost`, `KeywordOptimizer`, and `CrawlerService` can be constructed with a dedicated `ChatClient`
- tests instantiate those modules with explicit per-role `ChatClient` mocks rather than no-arg constructors

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-report-engine,bettafish-forum-engine,bettafish-insight-engine,bettafish-mind-spider test`

Expected: FAIL because the four missing bean pairs and dedicated constructors do not exist yet.

### Task 2: Implement the missing 4 client/model bean pairs

**Files:**
- Modify: `bettafish-common/src/main/java/com/bettafish/common/config/LlmAutoConfiguration.java`

**Step 1: Write minimal implementation**

Add:
- `reportChatModel` / `reportChatClient`
- `forumHostChatModel` / `forumHostChatClient`
- `keywordOptimizerChatModel` / `keywordOptimizerChatClient`
- `mindspiderChatModel` / `mindspiderChatClient`

Each pair should mirror the existing query/media/insight pattern and use the corresponding `BettaFishProperties.LlmProperties` section.

**Step 2: Run test to verify common config tests pass**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common test`

Expected: PASS.

### Task 3: Wire dedicated qualified injection into role services

**Files:**
- Modify: `bettafish-report-engine/src/main/java/com/bettafish/report/ReportAgent.java`
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumHost.java`
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/keyword/KeywordOptimizer.java`
- Modify: `bettafish-mind-spider/src/main/java/com/bettafish/spider/CrawlerService.java`

**Step 1: Write minimal implementation**

Update constructors to accept:
- `@Qualifier("reportChatClient") ChatClient`
- `@Qualifier("forumHostChatClient") ChatClient`
- `@Qualifier("keywordOptimizerChatClient") ChatClient`
- `@Qualifier("mindspiderChatClient") ChatClient`

Store the dependency even if current placeholder behavior does not use it yet. Keep existing test-friendly secondary constructors if needed.

**Step 2: Run focused verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine,bettafish-forum-engine,bettafish-insight-engine,bettafish-mind-spider test`

Expected: PASS.

### Task 4: Run full verification

**Files:**
- No additional code changes expected

**Step 1: Run full verification**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: PASS across all modules.
