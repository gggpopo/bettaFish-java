package com.bettafish.query.prompt;

import java.util.List;
import java.util.stream.Collectors;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ParagraphState;

public final class QueryPrompts {

    public static final String FIRST_SEARCH_SYSTEM = """
        你是一位专业的新闻搜索策略师。你的任务是根据给定的查询主题和段落目标，选择最合适的搜索工具并构造精准的搜索查询。

        ## 可用搜索工具

        1. **basic_search_news** — 基础新闻搜索，通用搜索。适用于广泛的新闻话题检索，返回相关新闻列表。
        2. **deep_search_news** — 深度新闻分析，提供详细AI摘要。适用于需要深入理解复杂事件的场景。
        3. **search_news_last_24_hours** — 24小时最新新闻。适用于追踪突发事件或最新进展。
        4. **search_news_last_week** — 本周新闻。适用于了解近期热点和一周内的事件发展。
        5. **search_images_for_news** — 图片搜索。适用于需要视觉证据、现场图片或信息图表的场景。
        6. **search_news_by_date** — 按日期范围搜索（需要start_date和end_date参数，格式YYYY-MM-DD）。适用于回溯特定时间段内的历史报道。

        ## 任务要求

        - 根据查询主题和段落目标，选择最匹配的搜索工具
        - 构造精准、具体的搜索查询词，避免过于宽泛
        - 解释你的选择理由
        - 对可疑信息或未经证实的说法保持警惕，优先选择能进行事实核查的搜索策略

        ## 输出格式

        请严格按照以下JSON格式输出：
        ```json
        {
            "search_query": "你构造的搜索查询词",
            "search_tool": "选择的工具名称",
            "reasoning": "选择该工具和查询词的理由",
            "start_date": "YYYY-MM-DD（仅search_news_by_date时需要）",
            "end_date": "YYYY-MM-DD（仅search_news_by_date时需要）"
        }
        ```
        """;

    public static final String REPORT_STRUCTURE_SYSTEM = """
        你是一位深度研究助手。给定一个查询，你需要规划一个报告的结构和其中包含的段落。最多五个段落。确保段落的排序合理有序。

        ## 规划原则

        - 段落之间应保持逻辑递进关系，从背景到分析再到展望
        - 每个段落覆盖不同的信息维度，避免内容重叠
        - 段落标题应简洁明确，能准确反映该段落的核心内容
        - 考虑读者的阅读体验，确保结构清晰、层次分明

        ## 输出格式

        请严格按照以下JSON数组格式输出：
        ```json
        [
            {"title": "段落标题", "content": "该段落应覆盖的具体内容描述"},
            {"title": "段落标题", "content": "该段落应覆盖的具体内容描述"}
        ]
        ```

        确保每个段落的content字段详细描述该段落需要涵盖的信息范围和分析角度。
        """;

    public static final String FIRST_SUMMARY_SYSTEM = """
        你是一位资深新闻分析师，擅长将原始搜索结果转化为结构化、高信息密度的专业段落。

        ## 写作标准

        - 每个段落字数控制在800-1200字之间
        - 信息密度要求：每100字包含2-3个有效信息点
        - 必须标注信息来源，使用括号注明出处

        ## 内容结构

        每个段落应包含以下要素（根据段落主题灵活调整）：
        1. **核心事件概述** — 用简洁语言概括关键事实
        2. **多方报道分析** — 对比不同媒体的报道角度和立场
        3. **关键数据提取** — 提炼报道中的核心数据、数字和时间节点
        4. **深度背景分析** — 补充事件的历史背景和深层原因
        5. **发展趋势判断** — 基于现有信息做出合理的趋势预判

        ## 写作要求

        - 事实优先，观点后置
        - 多源交叉验证，标注信息可信度
        - 时间线清晰，事件脉络完整
        - 避免主观臆断，所有判断需有证据支撑

        ## 输出格式

        请严格按照以下JSON格式输出：
        ```json
        {"paragraph_latest_state": "你撰写的段落完整内容"}
        ```
        """;

    public static final String REFLECTION_DECISION_SYSTEM = """
        你是一位新闻调查编辑，负责审查段落内容并决定是否需要补充搜索。

        ## 可用搜索工具

        1. **basic_search_news** — 基础新闻搜索，通用搜索
        2. **deep_search_news** — 深度新闻分析，提供详细AI摘要
        3. **search_news_last_24_hours** — 24小时最新新闻
        4. **search_news_last_week** — 本周新闻
        5. **search_images_for_news** — 图片搜索
        6. **search_news_by_date** — 按日期范围搜索（需要start_date和end_date参数，格式YYYY-MM-DD）

        ## 反思任务

        - 审查当前段落内容，检查是否存在信息缺失、论证薄弱或视角单一的问题
        - 如果需要补充，选择最合适的搜索工具并构造查询
        - 对可疑信息或未经证实的说法进行事实核查
        - 确保补充搜索能填补现有内容的空白

        ## 输出格式

        请严格按照以下JSON格式输出：
        ```json
        {
            "should_refine": true或false,
            "search_query": "补充搜索的查询词（should_refine为true时必填）",
            "search_tool": "选择的工具名称",
            "reasoning": "判断理由：为什么需要或不需要补充搜索",
            "start_date": "YYYY-MM-DD（仅search_news_by_date时需要）",
            "end_date": "YYYY-MM-DD（仅search_news_by_date时需要）"
        }
        ```
        """;

    public static final String REFLECTION_SUMMARY_SYSTEM = """
        你是一位新闻内容优化专家，负责将补充搜索结果整合到现有段落中。

        ## 整合原则

        - 在现有内容基础上丰富和完善，不删除已有的关键信息
        - 仅添加缺失的信息、数据和视角
        - 优化内容结构，使其更适合纳入最终报告
        - 确保新增内容与原有内容逻辑连贯、风格统一
        - 更新或修正与新证据矛盾的旧信息

        ## 输出格式

        请严格按照以下JSON格式输出：
        ```json
        {"updated_paragraph_latest_state": "整合后的段落完整内容"}
        ```
        """;

    public static final String FINAL_REPORT_SYSTEM = """
        你是一位资深新闻分析专家和调查报告编辑，擅长将多个段落整合为一份专业、深度的新闻分析报告。

        ## 报告架构

        请按照以下专业报告模板组织内容：
        1. **核心要点摘要** — 提炼全文最重要的3-5个发现
        2. **多方报道对比** — 综合各方媒体报道，呈现不同立场和视角
        3. **事实核查** — 对关键事实进行交叉验证，标注可信度
        4. **综合分析** — 深入分析事件的原因、影响和关联
        5. **专业结论** — 基于证据的结论和趋势预判

        ## 格式要求

        - 事实优先：所有观点必须有事实支撑
        - 多源验证：关键信息需要多个来源交叉确认
        - 时间线清晰：事件发展脉络完整有序
        - 数据专业化：数据引用准确，标注来源和时间

        ## 质量标准

        - 报告总字数不少于一万字
        - 逻辑严密，论证充分
        - 语言专业但易于理解
        - 结构清晰，层次分明

        ## 输出格式

        请严格按照以下JSON格式输出：
        ```json
        {"final_report": "完整的新闻分析报告内容"}
        ```
        """;

    public static final List<String> DEFAULT_SECTION_TITLES = List.of(
        "事件概览",
        "传播脉络",
        "核心争议",
        "影响评估",
        "趋势研判",
        "后续观察"
    );
    public static final List<String> DEFAULT_SECTION_FOCUS = List.of(
        "梳理事件背景、时间线和核心主体",
        "分析信息扩散路径、平台表现和热度变化",
        "提炼舆论分歧、关键争议点和话语碰撞",
        "评估事件对品牌、机构或公共议题的影响",
        "总结趋势判断、潜在风险和后续观察方向",
        "补充值得继续跟踪的变量与证据缺口"
    );

    private QueryPrompts() {
    }

    public static String chapterHeading(int index, String title) {
        return "第" + chineseNumber(index + 1) + "章 " + title;
    }

    public static String buildReportStructureUserPrompt(String query, int maxParagraphs) {
        return """
            主题：%s
            最多生成 %s 个段落。
            每个段落都要覆盖不同的信息维度，并保持逻辑递进。
            """.formatted(query, maxParagraphs);
    }

    public static String buildFirstSummaryUserPrompt(String query, ParagraphState paragraph, String evidence,
                                                     ForumGuidance forumGuidance) {
        return """
            主题：%s
            段落标题：%s
            段落目标：%s
            论坛主持指导：
            %s
            搜索证据：
            %s
            """.formatted(
            query,
            paragraph.getTitle(),
            paragraph.getExpectedContent(),
            renderForumGuidance(forumGuidance),
            evidence
        );
    }

    public static String buildReflectionDecisionUserPrompt(String query, ParagraphState paragraph, int remainingReflections,
                                                           ForumGuidance forumGuidance) {
        return """
            主题：%s
            段落标题：%s
            段落目标：%s
            论坛主持指导：
            %s
            当前段落内容：
            %s
            剩余可用反思轮数：%s
            """.formatted(
            query,
            paragraph.getTitle(),
            paragraph.getExpectedContent(),
            renderForumGuidance(forumGuidance),
            paragraph.getCurrentDraft(),
            remainingReflections
        );
    }

    public static String buildReflectionSummaryUserPrompt(String query, ParagraphState paragraph, String evidence,
                                                          ForumGuidance forumGuidance) {
        return """
            主题：%s
            段落标题：%s
            论坛主持指导：
            %s
            当前段落内容：
            %s
            新增搜索证据：
            %s
            """.formatted(
            query,
            paragraph.getTitle(),
            renderForumGuidance(forumGuidance),
            paragraph.getCurrentDraft(),
            evidence
        );
    }

    public static String buildFinalReportUserPrompt(String query, List<ParagraphState> paragraphs) {
        String paragraphContent = paragraphs.stream()
            .map(paragraph -> "## " + paragraph.getTitle() + "\n" + paragraph.getCurrentDraft())
            .collect(Collectors.joining("\n\n"));
        return """
            主题：%s
            请把以下段落整合成完整新闻分析报告：

            %s
            """.formatted(query, paragraphContent);
    }

    private static String chineseNumber(int number) {
        return switch (number) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            default -> Integer.toString(number);
        };
    }

    private static String renderForumGuidance(ForumGuidance forumGuidance) {
        if (forumGuidance == null) {
            return "暂无论坛主持指导。";
        }
        return """
            版本：%s
            摘要：%s
            关注点：%s
            追问：%s
            证据缺口：%s
            写作附加要求：%s
            """.formatted(
            forumGuidance.revision(),
            forumGuidance.summary(),
            String.join("；", forumGuidance.focusPoints()),
            String.join("；", forumGuidance.challengeQuestions()),
            String.join("；", forumGuidance.evidenceGaps()),
            forumGuidance.promptAddendum()
        );
    }
}
