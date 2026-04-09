# Report IR And Chapter Generation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade report generation from flat `List<ReportSection>` assembly to a formal `DocumentIr` pipeline with chapter-level structured generation, validation, retry, placeholder fallback, and IR-driven HTML rendering.

**Architecture:** Introduce `DocumentIr{meta, blocks}` in `bettafish-common` so `ReportDocument` can expose a durable intermediate representation without depending on `bettafish-report-engine`. Keep a compatibility projection from IR back to `ReportSection` so existing app/tests survive while callers migrate. In `bettafish-report-engine`, add a `ChapterGenerationNode` that generates one chapter at a time through `LlmGateway.callJson(...)`, validates the returned IR blocks, checks density, retries with stricter prompts, and falls back to a placeholder block sequence when quality gates still fail. `HtmlRenderer` will render `DocumentIr` only.

**Tech Stack:** Java 21, Spring Boot, Jackson, Spring AI, JUnit 5

### Task 1: Introduce Common Document IR

**Files:**
- Create: `bettafish-common/src/main/java/com/bettafish/common/api/DocumentIr.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/api/DocumentMeta.java`
- Create: `bettafish-common/src/main/java/com/bettafish/common/api/DocumentBlock.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/api/ReportDocument.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/api/ReportSection.java`
- Create: `bettafish-common/src/test/java/com/bettafish/common/api/ReportDocumentTest.java`

**Step 1: Write the failing test**

Add tests that expect `ReportDocument` to expose `documentIr()` and project legacy `sections()` from heading/paragraph IR blocks.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportDocumentTest test`
Expected: FAIL because `DocumentIr` and compatibility projection do not exist.

**Step 3: Write minimal implementation**

Add the common IR types with the minimal block set:
- `heading`
- `paragraph`
- `list`
- `quote`
- `table`
- `code`
- `link`

Make `ReportDocument` hold `title`, `summary`, `documentIr`, and `html`, while keeping a compatibility constructor from `List<ReportSection>` and a computed `sections()` projection.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportDocumentTest test`
Expected: PASS

### Task 2: Decouple HtmlRenderer To Render Document IR

**Files:**
- Modify: `bettafish-report-engine/src/main/java/com/bettafish/report/renderer/HtmlRenderer.java`
- Create: `bettafish-report-engine/src/test/java/com/bettafish/report/renderer/HtmlRendererTest.java`

**Step 1: Write the failing test**

Add a renderer test that passes a `DocumentIr` containing each supported block type and asserts the generated HTML contains the expected tags and escaped content.

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=HtmlRendererTest test`
Expected: FAIL because `HtmlRenderer` still accepts `title/summary/sections`.

**Step 3: Write minimal implementation**

Change `HtmlRenderer.render(...)` to accept a `DocumentIr` and render `meta` plus each block type deterministically.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=HtmlRendererTest test`
Expected: PASS

### Task 3: Add ChapterGenerationNode With Quality Gates

**Files:**
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/node/ChapterGenerationNode.java`
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/ir/ChapterSpec.java`
- Create: `bettafish-report-engine/src/main/java/com/bettafish/report/ir/ChapterGenerationResult.java`
- Modify: `bettafish-report-engine/src/main/java/com/bettafish/report/prompt/ReportPrompts.java`
- Create: `bettafish-report-engine/src/test/java/com/bettafish/report/node/ChapterGenerationNodeTest.java`

**Step 1: Write the failing test**

Add tests that verify:
- invalid/empty blocks trigger retry
- low-density content triggers retry
- repeated failure yields a placeholder chapter
- successful retry returns validated blocks

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChapterGenerationNodeTest test`
Expected: FAIL because chapter generation node and quality gates do not exist.

**Step 3: Write minimal implementation**

Implement per-chapter generation through `LlmGateway.callJson(...)`, validate block structure, enforce a minimum density heuristic, retry a bounded number of times, and fall back to a placeholder heading + paragraph chapter.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ChapterGenerationNodeTest test`
Expected: PASS

### Task 4: Rebuild ReportAgent Around Document IR

**Files:**
- Modify: `bettafish-report-engine/src/main/java/com/bettafish/report/ReportAgent.java`
- Modify: `bettafish-report-engine/src/test/java/com/bettafish/report/ReportAgentTest.java`
- Modify: `bettafish-common/src/main/java/com/bettafish/common/engine/ReportGenerator.java`

**Step 1: Write the failing test**

Update `ReportAgentTest` to expect:
- one chapter per engine plus one forum chapter
- `documentIr().blocks()` to contain generated chapter blocks
- `htmlRenderer` output to come from IR rendering
- chapter fallback placeholders when generation quality stays below threshold

**Step 2: Run test to verify it fails**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportAgentTest test`
Expected: FAIL because the agent still builds flat sections directly.

**Step 3: Write minimal implementation**

Switch `ReportAgent` from direct `ChatClient` usage to `LlmGateway`, build chapter specs from engine/forum inputs, call `ChapterGenerationNode` per chapter, assemble `DocumentIr`, and render HTML from IR.

**Step 4: Run test to verify it passes**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-report-engine -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportAgentTest test`
Expected: PASS

### Task 5: Update App/Test Compatibility And Verify Build

**Files:**
- Modify: `bettafish-app/src/test/java/com/bettafish/app/controller/AnalysisControllerTest.java`
- Modify: `bettafish-app/src/test/java/com/bettafish/app/service/AnalysisCoordinatorTest.java`
- Modify only files above as needed

**Step 1: Write/update failing compatibility assertions**

Update app tests to create `ReportDocument` through the new IR-aware constructor and assert title/report serialization still works.

**Step 2: Run focused tests to verify failure or gaps**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-report-engine,bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportDocumentTest,HtmlRendererTest,ChapterGenerationNodeTest,ReportAgentTest,AnalysisCoordinatorTest,AnalysisControllerTest test`
Expected: FAIL until all call sites are migrated.

**Step 3: Write minimal compatibility implementation**

Adjust tests and any touched constructors/accessors until the new IR contract is wired through.

**Step 4: Run focused tests to verify they pass**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test -pl bettafish-common,bettafish-report-engine,bettafish-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ReportDocumentTest,HtmlRendererTest,ChapterGenerationNodeTest,ReportAgentTest,AnalysisCoordinatorTest,AnalysisControllerTest test`
Expected: PASS

### Task 6: Full Verification

**Files:**
- Modify only files above as needed

**Step 1: Run full test suite**

Run: `mvn -Dmaven.repo.local=/tmp/bettafish-m2-test test`
Expected: PASS
