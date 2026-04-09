# Prompt 工程对齐 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 重写优化 5 个引擎的 Prompt 文件，补充完整工具 Schema，对齐目标项目的分析质量。

**Architecture:** 每个 Prompt 文件独立重写，保持现有 Java 方法签名不变（避免破坏调用方），仅扩展 system prompt 内容和补充工具描述常量。采用 Java text block 存储长文本 prompt。

**Tech Stack:** Java 21 text blocks, Spring AI ChatClient (已有)

---

### Task 1: QueryPrompts.java — 重写系统提示词 + 补充工具 Schema

**Files:**
- Modify: `bettafish-query-engine/src/main/java/com/bettafish/query/prompt/QueryPrompts.java`
- Test: `bettafish-query-engine/src/test/java/com/bettafish/query/prompt/QueryPromptsTest.java`

**Step 1: Write the failing test**

```java
package com.bettafish.query.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QueryPromptsTest {

    @Test
    void reportStructureSystem_containsStructuralGuidance() {
        assertThat(QueryPrompts.REPORT_STRUCTURE_SYSTEM)
            .contains("JSON")
            .contains("段落");
    }

    @Test
    void firstSearchSystem_containsToolDescriptions() {
        assertThat(QueryPrompts.FIRST_SEARCH_SYSTEM)
            .contains("basic_search_news")
            .contains("deep_search_news")
            .contains("search_news_last_24_hours")
            .contains("search_news_last_week")
            .contains("search_images_for_news")
            .contains("search_news_by_date");
    }

    @Test
    void firstSummarySystem_containsWritingStandards() {
        assertThat(QueryPrompts.FIRST_SUMMARY_SYSTEM)
            .contains("800")
            .contains("信息密度");
    }

    @Test
    void reflectionSystem_containsToolList() {
        assertThat(QueryPrompts.REFLECTION_DECISION_SYSTEM)
            .contains("basic_search_news")
            .contains("search_news_by_date");
    }

    @Test
    void reportFormattingSystem_containsWordRequirement() {
        assertThat(QueryPrompts.FINAL_REPORT_SYSTEM)
            .contains("万字")
            .contains("事实");
    }

    @Test
    void buildFirstSummaryUserPrompt_includesAllFields() {
        var paragraph = new com.bettafish.common.model.ParagraphState();
        paragraph.setTitle("测试标题");
        paragraph.setExpectedContent("测试内容");
        var result = QueryPrompts.buildFirstSummaryUserPrompt(
            "测试主题", paragraph, "证据内容", null);
        assertThat(result).contains("测试主题").contains("测试标题").contains("证据内容");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-query-engine -Dtest=QueryPromptsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — new assertions don't match current stub prompts

**Step 3: Rewrite QueryPrompts.java**

Replace the entire prompt content. Key changes:
- `REPORT_STRUCTURE_SYSTEM`: 添加报告规划要求（最多5段、逻辑递进、JSON Schema 输出约束）
- `FIRST_SEARCH_SYSTEM`: 补充 6 个新闻搜索工具的完整描述（basic_search_news, deep_search_news, search_news_last_24_hours, search_news_last_week, search_images_for_news, search_news_by_date），包含适用场景、特点、参数要求，以及 JSON 输出 Schema
- `FIRST_SUMMARY_SYSTEM`: 重写为专业新闻分析师角色，添加撰写标准（开篇框架、信息层次、结构化组织、引用要求、信息密度要求 800-1200 字/段）
- `REFLECTION_DECISION_SYSTEM`: 补充 6 工具列表 + 反思任务指令（遗漏检查、工具选择、谣言核查）
- `REFLECTION_SUMMARY_SYSTEM`: 添加迭代完善指令（保留关键信息、只添加缺失信息）
- `FINAL_REPORT_SYSTEM`: 重写为万字级专业新闻分析报告格式化指令，包含报告架构模板（核心要点摘要、多方报道对比、事实核查、综合分析、专业结论）、格式化要求（事实优先、多源验证、时间线、数据专业化）、质量控制标准

保持所有现有 builder 方法签名不变，仅扩展 system prompt 常量。

**Step 4: Run test to verify it passes**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-query-engine -Dtest=QueryPromptsTest`
Expected: PASS

**Step 5: Commit**

```bash
git add bettafish-query-engine/src/main/java/com/bettafish/query/prompt/QueryPrompts.java
git add bettafish-query-engine/src/test/java/com/bettafish/query/prompt/QueryPromptsTest.java
git commit -m "feat(query): rewrite QueryPrompts with full tool schemas and professional analysis instructions"
```

---

### Task 2: InsightPrompts.java — 重写系统提示词 + 补充舆情工具 Schema

**Files:**
- Modify: `bettafish-insight-engine/src/main/java/com/bettafish/insight/prompt/InsightPrompts.java`
- Test: `bettafish-insight-engine/src/test/java/com/bettafish/insight/prompt/InsightPromptsTest.java`

**Step 1: Write the failing test**

```java
package com.bettafish.insight.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InsightPromptsTest {

    @Test
    void firstSearchSystem_containsSentimentTools() {
        assertThat(InsightPrompts.FIRST_SEARCH_SYSTEM)
            .doesNotStartWith("InsightAgent mines")  // no longer a stub
            .contains("search_hot_content")
            .contains("search_topic_globally")
            .contains("search_topic_by_date")
            .contains("get_comments_for_topic")
            .contains("search_topic_on_platform")
            .contains("analyze_sentiment");
    }

    @Test
    void firstSummarySystem_containsDataRequirements() {
        assertThat(InsightPrompts.FIRST_SUMMARY_SYSTEM)
            .contains("800")
            .contains("评论");
    }

    @Test
    void reflectionSystem_emphasizesAuthenticVoices() {
        assertThat(InsightPrompts.REFLECTION_DECISION_SYSTEM)
            .contains("search_hot_content")
            .contains("民意");
    }

    @Test
    void keywordOptimizerSystem_containsColloquialGuidance() {
        assertThat(InsightPrompts.KEYWORD_OPTIMIZER_SYSTEM)
            .contains("口语化").or(assertThat(InsightPrompts.KEYWORD_OPTIMIZER_SYSTEM).contains("接地气"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-insight-engine -Dtest=InsightPromptsTest -Dsurefire.failIfNoSpecifiedTests=false`

**Step 3: Rewrite InsightPrompts.java**

Key changes:
- `FIRST_SEARCH_SYSTEM` (was a one-liner stub): 重写为完整的舆情分析师角色，补充 6 个舆情工具描述（search_hot_content 含 time_period/limit/enable_sentiment 参数, search_topic_globally 含 limit_per_table, search_topic_by_date 含 start_date/end_date, get_comments_for_topic 含 limit, search_topic_on_platform 含 platform 必选参数, analyze_sentiment 含 texts 参数）。添加"挖掘真实民意"核心使命、接地气搜索词设计原则（避免官方术语、使用网民真实表达）、不同平台语言特色参考、情感表达词汇库
- `FIRST_SUMMARY_SYSTEM`: 重写为舆情分析段落撰写标准（800-1200字、大量引用原始评论 5-8 条、精确数据统计、情感分析数据、多层次深度分析）
- `REFLECTION_DECISION_SYSTEM`: 补充 6 工具列表 + 反思核心目标（让报告更有人情味、识别信息缺口、精准补充查询、搜索词优化示例）
- `REFLECTION_SUMMARY_SYSTEM`: 添加内容扩充策略（目标 1000-1500 字、保留精华大量补充、数据密集化、情感分析升级）
- `FINAL_CONCLUSION_SYSTEM`: 保持现有结构化输出约束
- `KEYWORD_OPTIMIZER_SYSTEM`: 添加口语化/接地气搜索词指导

保持所有 builder 方法签名不变。

**Step 4: Run test**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-insight-engine -Dtest=InsightPromptsTest`

**Step 5: Commit**

```bash
git add bettafish-insight-engine/src/main/java/com/bettafish/insight/prompt/InsightPrompts.java
git add bettafish-insight-engine/src/test/java/com/bettafish/insight/prompt/InsightPromptsTest.java
git commit -m "feat(insight): rewrite InsightPrompts with 6 sentiment tools and colloquial search guidance"
```

---

### Task 3: MediaPrompts.java — 重写系统提示词 + 补充多模态工具 Schema

**Files:**
- Modify: `bettafish-media-engine/src/main/java/com/bettafish/media/prompt/MediaPrompts.java`
- Test: `bettafish-media-engine/src/test/java/com/bettafish/media/prompt/MediaPromptsTest.java`

**Step 1: Write the failing test**

```java
package com.bettafish.media.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MediaPromptsTest {

    @Test
    void firstSearchSystem_containsMultimodalTools() {
        // Need a new FIRST_SEARCH_SYSTEM constant with tool descriptions
        assertThat(MediaPrompts.FIRST_SEARCH_SYSTEM)
            .doesNotStartWith("MediaAgent summarized")
            .contains("comprehensive_search")
            .contains("web_search_only")
            .contains("search_for_structured_data")
            .contains("search_last_24_hours")
            .contains("search_last_week");
    }

    @Test
    void firstSummarySystem_containsMultimodalAnalysis() {
        assertThat(MediaPrompts.FIRST_SUMMARY_SYSTEM)
            .contains("多模态")
            .contains("800");
    }

    @Test
    void finalReportSystem_exists() {
        assertThat(MediaPrompts.FINAL_REPORT_SYSTEM)
            .contains("万字")
            .contains("全景");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-media-engine -Dtest=MediaPromptsTest -Dsurefire.failIfNoSpecifiedTests=false`

**Step 3: Rewrite MediaPrompts.java**

Key changes:
- Add new `FIRST_SEARCH_SYSTEM` constant (was a one-liner): 完整的多模态搜索工具描述（comprehensive_search 全面综合搜索、web_search_only 纯网页搜索、search_for_structured_data 结构化数据查询、search_last_24_hours 24小时搜索、search_last_week 本周搜索），包含适用场景和特点
- `FIRST_SUMMARY_SYSTEM`: 重写为多媒体内容分析师角色，添加多模态内容整合要求（网页内容分析、图片信息解读、AI总结整合、结构化数据应用），800-1200字/段
- `REFLECTION_DECISION_SYSTEM`: 补充 5 工具列表 + 多模态反思指令
- `REFLECTION_SUMMARY_SYSTEM`: 添加多维度深化分析（关联分析、对比分析、趋势分析）
- Add new `FINAL_REPORT_SYSTEM` constant: 万字级全景式多媒体分析报告格式化指令，包含创新架构（多维信息摘要、视觉内容深度解析、跨媒体综合分析）
- Keep `NARRATIVE_OUTPUT_SCHEMA` and all builder methods unchanged

**Step 4: Run test**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-media-engine -Dtest=MediaPromptsTest`

**Step 5: Commit**

```bash
git add bettafish-media-engine/src/main/java/com/bettafish/media/prompt/MediaPrompts.java
git add bettafish-media-engine/src/test/java/com/bettafish/media/prompt/MediaPromptsTest.java
git commit -m "feat(media): rewrite MediaPrompts with 5 multimodal tools and panoramic report formatting"
```

---

### Task 4: ForumPrompts.java — 重写主持人提示词

**Files:**
- Modify: `bettafish-forum-engine/src/main/java/com/bettafish/forum/prompt/ForumPrompts.java`
- Test: `bettafish-forum-engine/src/test/java/com/bettafish/forum/prompt/ForumPromptsTest.java`

**Step 1: Write the failing test**

```java
package com.bettafish.forum.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ForumPromptsTest {

    @Test
    void hostSystemPrompt_containsRoleDefinition() {
        assertThat(ForumPrompts.HOST_SYSTEM_PROMPT)
            .contains("事件梳理")
            .contains("纠正错误")
            .contains("整合观点")
            .contains("趋势预测");
    }

    @Test
    void hostSystemPrompt_containsAgentDescriptions() {
        assertThat(ForumPrompts.HOST_SYSTEM_PROMPT)
            .contains("INSIGHT")
            .contains("MEDIA")
            .contains("QUERY");
    }

    @Test
    void buildGuidanceUserPrompt_containsStructuredSections() {
        var transcript = java.util.List.of(
            new com.bettafish.common.model.ForumMessage("agent", "INSIGHT", "测试内容")
        );
        var result = ForumPrompts.buildGuidanceUserPrompt(transcript, 1);
        assertThat(result)
            .contains("事件梳理")
            .contains("观点整合")
            .contains("深层次分析")
            .contains("问题引导");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-forum-engine -Dtest=ForumPromptsTest -Dsurefire.failIfNoSpecifiedTests=false`

**Step 3: Rewrite ForumPrompts.java**

Key changes:
- `HOST_SYSTEM_PROMPT`: 从简单的 JSON 输出指令重写为完整的主持人角色定义，包含 6 项职责（事件梳理、引导讨论、纠正错误、整合观点、趋势预测、推进分析）、3 个 Agent 介绍（INSIGHT 私有数据库挖掘、MEDIA 多模态内容分析、QUERY 精准信息搜索）、发言要求（1000字以内、结构清晰、深入分析、客观中立、前瞻性）。保留 JSON 输出格式约束（ForumGuidance 结构）
- `buildGuidanceUserPrompt`: 扩展为结构化的用户提示，包含四个分析维度（事件梳理与时间线分析、观点整合与对比分析、深层次分析与趋势预测、问题引导与讨论方向）

**Step 4: Run test**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-forum-engine -Dtest=ForumPromptsTest`

**Step 5: Commit**

```bash
git add bettafish-forum-engine/src/main/java/com/bettafish/forum/prompt/ForumPrompts.java
git add bettafish-forum-engine/src/test/java/com/bettafish/forum/prompt/ForumPromptsTest.java
git commit -m "feat(forum): rewrite ForumPrompts with full moderator role and structured discussion guidance"
```

---

### Task 5: ReportPrompts.java — 重写报告生成提示词 + 补充模板/布局/字数规划

**Files:**
- Modify: `bettafish-report-engine/src/main/java/com/bettafish/report/prompt/ReportPrompts.java`
- Test: `bettafish-report-engine/src/test/java/com/bettafish/report/prompt/ReportPromptsTest.java`

**Step 1: Write the failing test**

```java
package com.bettafish.report.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReportPromptsTest {

    @Test
    void chapterGenerationSystem_containsBlockTypes() {
        assertThat(ReportPrompts.CHAPTER_GENERATION_SYSTEM_PROMPT)
            .contains("heading")
            .contains("paragraph")
            .contains("list")
            .contains("table")
            .contains("widget");
    }

    @Test
    void templateSelectionSystem_exists() {
        assertThat(ReportPrompts.TEMPLATE_SELECTION_SYSTEM)
            .contains("模板");
    }

    @Test
    void documentLayoutSystem_exists() {
        assertThat(ReportPrompts.DOCUMENT_LAYOUT_SYSTEM)
            .contains("标题")
            .contains("目录");
    }

    @Test
    void wordBudgetSystem_exists() {
        assertThat(ReportPrompts.WORD_BUDGET_SYSTEM)
            .contains("字数");
    }

    @Test
    void chapterRepairSystem_exists() {
        assertThat(ReportPrompts.CHAPTER_REPAIR_SYSTEM)
            .contains("修复");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-report-engine -Dtest=ReportPromptsTest -Dsurefire.failIfNoSpecifiedTests=false`

**Step 3: Rewrite ReportPrompts.java**

Key changes:
- `CHAPTER_GENERATION_SYSTEM_PROMPT`: 从简单的 block 类型列表重写为完整的"章节装配工厂"指令，补充 IR 版本约束、Block 类型详细说明（heading 含 anchor、paragraph 含 inlines/marks、list 含二维 items、table 含 rows/cells/align、widget 含 Chart.js 配置、kpiGrid、hr、callout、blockquote、engineQuote、math）、SWOT/PEST 块使用限制、编号规则（一级中文数字、二级阿拉伯数字）、JSON 自检规则
- Add `TEMPLATE_SELECTION_SYSTEM`: 模板选择助手，包含 6 种报告模板类型（企业品牌声誉、市场竞争格局、日常舆情监测、政策行业动态、社会公共热点、突发事件危机公关）及选择标准
- Add `DOCUMENT_LAYOUT_SYSTEM`: 报告首席设计官角色，包含标题/导语区/目录样式/美学要素设计指令，tocPlan 结构约束
- Add `WORD_BUDGET_SYSTEM`: 篇幅规划官角色，总字数约 40000 字，章节字数分配策略
- Add `CHAPTER_REPAIR_SYSTEM`: 章节 JSON 修复官角色，最小修改原则
- Add `CHAPTER_RECOVERY_SYSTEM`: 跨引擎 JSON 抢修官角色
- Add builder methods: `buildChapterRepairUserPrompt`, `buildChapterRecoveryUserPrompt`, `buildDocumentLayoutUserPrompt`, `buildWordBudgetUserPrompt`

**Step 4: Run test**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -pl bettafish-report-engine -Dtest=ReportPromptsTest`

**Step 5: Commit**

```bash
git add bettafish-report-engine/src/main/java/com/bettafish/report/prompt/ReportPrompts.java
git add bettafish-report-engine/src/test/java/com/bettafish/report/prompt/ReportPromptsTest.java
git commit -m "feat(report): rewrite ReportPrompts with template selection, layout, word budget and chapter repair"
```

---

### Task 6: 全模块编译验证

**Files:** None (verification only)

**Step 1: Run full build**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw compile -q`
Expected: BUILD SUCCESS

**Step 2: Run all tests**

Run: `cd /Users/bytedance/Downloads/xiaogaoagent/bettafish && ./mvnw test -q`
Expected: All tests pass

**Step 3: Final commit if any fixups needed**

```bash
git add -A
git commit -m "fix: resolve compilation issues from prompt engineering rewrite"
```
