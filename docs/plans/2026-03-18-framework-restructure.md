# BettaFish Framework Restructure Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restructure the current multi-module BettaFish Java skeleton so its package layout, class names, and Spring entry points align with the target BettaFish framework while preserving a runnable end-to-end skeleton.

**Architecture:** Use an incremental compatibility-first migration. Replace existing top-level skeleton classes with target framework class names and package locations, keep current placeholder behavior where real agent logic does not exist yet, and introduce new `model/event/util/prompt/tool/node` packages as minimal but compilable building blocks.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring AI 1.0.1, Maven multi-module build, JUnit 5, MockMvc

### Task 1: Restructure `bettafish-common`

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/config/BettaFishProperties.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/config/LlmAutoConfiguration.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/model/AgentState.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/model/ParagraphState.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/model/SearchDecision.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/model/ForumMessage.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/model/AnalysisResult.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AgentSpeechEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/HostCommentEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/event/AnalysisCompleteEvent.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/util/RetryHelper.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/util/JsonParser.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/util/DateValidator.java`
- Modify: `bettafish-common/src/test/java/com/bettafish/common/config/LlmAutoConfigurationTest.java`

**Step 1: Write the failing tests**

Add test coverage proving `LlmAutoConfiguration` now binds through `BettaFishProperties` and still creates `query/media/insight` model and client beans when API keys are present.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common test`

Expected: FAIL because `BettaFishProperties` and updated configuration wiring do not exist yet.

**Step 3: Write minimal implementation**

Create `BettaFishProperties` as the new global configuration root and add the new `model/event/util` skeleton classes with only the fields needed by current placeholder agents.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common test`

Expected: PASS for the `bettafish-common` test suite.

### Task 2: Rename Spring app entry points and orchestration packages

**Files:**
- Create: `bettafish-app/src/main/java/com/bettafish/app/BettaFishApplication.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/controller/AnalysisController.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/controller/WebSocketController.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/controller/CreateAnalysisTaskRequest.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisCoordinator.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisTaskRepository.java`
- Create: `bettafish-app/src/main/java/com/bettafish/app/service/InMemoryAnalysisTaskRepository.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/config/AnalysisExecutionConfiguration.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/api/AnalysisControllerTest.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/application/AnalysisCoordinatorTest.java`

**Step 1: Write the failing tests**

Update tests to target the new `controller` and `service` packages and the new `BettaFishApplication` boot class.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am test`

Expected: FAIL because the new package paths and class names do not exist yet.

**Step 3: Write minimal implementation**

Move the current controller and coordinator behavior into the new package layout and add a placeholder `WebSocketController` that exposes a stable endpoint contract.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am test`

Expected: PASS for the `bettafish-app` tests.

### Task 3: Rename engine skeletons to target agent classes

**Files:**
- Create: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Create: `bettafish-query-engine/src/main/java/com/bettafish/query/node/...`
- Create: `bettafish-query-engine/src/main/java/com/bettafish/query/tool/TavilySearchTool.java`
- Create: `bettafish-query-engine/src/main/java/com/bettafish/query/prompt/QueryPrompts.java`
- Create: `bettafish-media-engine/src/main/java/com/bettafish/media/MediaAgent.java`
- Create: `bettafish-media-engine/src/main/java/com/bettafish/media/node/...`
- Create: `bettafish-media-engine/src/main/java/com/bettafish/media/tool/...`
- Create: `bettafish-media-engine/src/main/java/com/bettafish/media/prompt/MediaPrompts.java`
- Create: `bettafish-insight-engine/src/main/java/com/bettafish/insight/InsightAgent.java`
- Create: `bettafish-insight-engine/src/main/java/com/bettafish/insight/node/...`
- Create: `bettafish-insight-engine/src/main/java/com/bettafish/insight/tool/MediaCrawlerDbTool.java`
- Create: `bettafish-insight-engine/src/main/java/com/bettafish/insight/tool/SentimentTool.java`
- Create: `bettafish-insight-engine/src/main/java/com/bettafish/insight/keyword/KeywordOptimizer.java`
- Create: `bettafish-insight-engine/src/main/java/com/bettafish/insight/prompt/InsightPrompts.java`
- Modify: `bettafish-insight-engine/src/test/java/com/bettafish/insight/InsightAnalysisEngineTest.java`

**Step 1: Write the failing tests**

Point the engine-specific tests at `QueryAgent`, `MediaAgent`, and `InsightAgent` and keep the existing placeholder result contracts intact.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-media-engine,bettafish-insight-engine -am test`

Expected: FAIL because the target agent classes do not exist.

**Step 3: Write minimal implementation**

Replace the current `*AnalysisEngine` skeletons with `*Agent` classes that still implement `AnalysisEngine`, and add minimal placeholder `node/tool/prompt/keyword` classes.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-media-engine,bettafish-insight-engine -am test`

Expected: PASS for the engine test suites.

### Task 4: Align forum, report, spider, and sentiment modules with target names

**Files:**
- Create: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumCoordinator.java`
- Create: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumHost.java`
- Create: `bettafish-forum-engine/src/main/java/com/bettafish/forum/prompt/ForumPrompts.java`
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/ReportAgent.java`
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/template/...`
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/ir/...`
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/renderer/HtmlRenderer.java`
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/prompt/ReportPrompts.java`
- Create: `bettafish-mind-spider/src/main/java/com/bettafish/spider/CrawlerService.java`
- Create: `bettafish-mind-spider/src/main/java/com/bettafish/spider/scheduler/...`
- Create: `bettafish-mind-spider/src/main/java/com/bettafish/spider/platform/...`
- Create: `bettafish-sentiment-mcp/src/main/java/com/bettafish/sentiment/SentimentMcpServer.java`
- Create: `bettafish-sentiment-mcp/src/main/java/com/bettafish/sentiment/OnnxSentimentAnalyzer.java`

**Step 1: Write the failing tests**

Add or update module tests so they reference the target class names and verify current placeholder behavior still works.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-forum-engine,bettafish-report-engine,bettafish-mind-spider,bettafish-sentiment-mcp -am test`

Expected: FAIL because the renamed framework classes do not exist.

**Step 3: Write minimal implementation**

Rename the current placeholder classes into the new package layout and add skeletal prompt/template/renderer/platform classes where the framework expects them.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-forum-engine,bettafish-report-engine,bettafish-mind-spider,bettafish-sentiment-mcp -am test`

Expected: PASS for the module test suites.

### Task 5: Full regression verification

**Files:**
- Verify only

**Step 1: Run the full Maven test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`

Expected: BUILD SUCCESS with all current module tests green.

**Step 2: Review the resulting tree**

Run: `find bettafish-* -maxdepth 4 -type d | sort`

Expected: The project layout matches the target framework at module/package level.
