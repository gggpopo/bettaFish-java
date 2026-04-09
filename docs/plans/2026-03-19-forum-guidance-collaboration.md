# Forum Guidance Collaboration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Turn ForumEngine into a real collaboration layer that accumulates agent transcript, generates structured `ForumGuidance` every N speeches, and feeds that guidance back into engine summary/reflection prompts.

**Architecture:** Define `ForumGuidance` and a read-only `ForumGuidanceProvider` in `bettafish-common` so engines can consume guidance without depending on the forum module. Implement a forum transcript service in `bettafish-forum-engine` that subscribes to `AgentSpeechEvent`, stores transcript/guidance history, triggers the host to produce JSON guidance every N speeches, and lets `ForumCoordinator` finalize a `ForumSummary` from that accumulated state. Wire all `AnalysisEventSubscriber` beans into the app event bus and update `QueryAgent` to publish speeches plus inject latest guidance into summary/reflection prompts.

**Tech Stack:** Java 21, Spring Boot, Spring AI, Jackson, JUnit 5

### Task 1: Add Shared Forum Guidance Contract

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/model/ForumGuidance.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/engine/ForumGuidanceProvider.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/api/ForumSummary.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/model/AgentState.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/model/ParagraphState.java`

**Step 1: Write the failing test**

Add tests that expect `ForumSummary` to expose transcript/guidance history defaults and expect query paragraph state to track applied guidance revision.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-forum-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest,ForumCoordinatorTest test`
Expected: FAIL because `ForumGuidance` and related fields do not exist.

**Step 3: Write minimal implementation**

Add the shared types and minimal state holders with convenience defaults.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-forum-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest,ForumCoordinatorTest test`
Expected: PASS

### Task 2: Implement Transcript Accumulation and Guidance Generation

**Files:**
- Create: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumTranscriptService.java`
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumHost.java`
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/ForumCoordinator.java`
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/prompt/ForumPrompts.java`
- Modify: `bettafish-forum-engine/src/test/java/com/bettafish/forum/ForumCoordinatorTest.java`

**Step 1: Write the failing test**

Add a test that publishes two `AgentSpeechEvent`s, verifies transcript accumulation, verifies one structured guidance is generated, verifies a `HostCommentEvent` is emitted, and verifies final `ForumSummary` contains transcript/guidance history.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-forum-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ForumCoordinatorTest test`
Expected: FAIL because there is no transcript service or structured guidance generation.

**Step 3: Write minimal implementation**

Use `LlmGateway.callJson(...)` in `ForumHost`, store task-scoped transcript/guidance in the transcript service, trigger guidance every 2 speeches by default, and have `ForumCoordinator` build summary from stored transcript/guidance.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-forum-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ForumCoordinatorTest test`
Expected: PASS

### Task 3: Register Forum Subscribers in App and Emit Engine Speeches

**Files:**
- Create: `bettafish-app/src/main/java/com/bettafish/app/event/AnalysisEventSubscriberRegistrar.java`
- Modify: `bettafish-app/src/main/java/com/bettafish/app/service/AnalysisCoordinator.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/service/AnalysisCoordinatorTest.java`

**Step 1: Write the failing test**

Add assertions that engine completion now emits `AgentSpeechEvent` with engine summary content so forum transcript can accumulate speeches even for one-shot engines.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`
Expected: FAIL because coordinator does not publish summary speeches and app does not register subscribers.

**Step 3: Write minimal implementation**

Publish one `AgentSpeechEvent` per engine result in the coordinator and add a registrar that subscribes all `AnalysisEventSubscriber` beans to the event bus.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AnalysisCoordinatorTest test`
Expected: PASS

### Task 4: Inject Forum Guidance into Query Prompts

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/QueryAgent.java`
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/prompt/QueryPrompts.java`
- Modify: `bettafish-query-engine/src/test/java/com/bettafish/query/QueryAgentTest.java`

**Step 1: Write the failing test**

Add a test that supplies a static `ForumGuidanceProvider`, runs the workflow, and asserts summary/reflection prompts include the guidance content. Also assert summary/reflection publication updates `AgentState` / `ParagraphState` with latest guidance metadata.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest test`
Expected: FAIL because query prompts do not consume forum guidance and query does not emit speech events.

**Step 3: Write minimal implementation**

Inject `ForumGuidanceProvider` into `QueryAgent`, publish `AgentSpeechEvent` after paragraph drafts are generated, sync transcript/guidance into `AgentState`, and inject latest guidance into summary/reflection user prompts.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest test`
Expected: PASS

### Task 5: Verify Build

**Files:**
- Modify only files above as needed

**Step 1: Run focused tests**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-query-engine,bettafish-forum-engine,bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=QueryAgentTest,ForumCoordinatorTest,AnalysisCoordinatorTest test`
Expected: PASS

**Step 2: Run full test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`
Expected: PASS
