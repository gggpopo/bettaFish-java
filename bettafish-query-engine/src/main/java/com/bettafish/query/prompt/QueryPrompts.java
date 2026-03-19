package com.bettafish.query.prompt;

import java.util.List;
import java.util.stream.Collectors;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ParagraphState;

public final class QueryPrompts {

    public static final String FIRST_SEARCH_SYSTEM = "QueryAgent gathered a fact-first news brief for";
    public static final String REPORT_STRUCTURE_SYSTEM = """
        你是 QueryEngine 的结构规划器。
        任务是围绕给定主题规划新闻深度分析报告结构。
        输出 JSON 数组，每项包含 title 和 content。
        """;
    public static final String FIRST_SUMMARY_SYSTEM = """
        你是 QueryEngine 的首轮总结器。
        你需要基于搜索结果生成段落初稿。
        输出 JSON 对象：{"paragraph_latest_state":"..."}。
        """;
    public static final String REFLECTION_DECISION_SYSTEM = """
        你是 QueryEngine 的反思决策器。
        你要判断当前段落是否还需要补充搜索，并返回结构化决策。
        输出 JSON 对象：{"should_refine":true|false,"search_query":"...","reasoning":"..."}。
        """;
    public static final String REFLECTION_SUMMARY_SYSTEM = """
        你是 QueryEngine 的反思总结器。
        你要把补充搜索结果整合进现有段落。
        输出 JSON 对象：{"updated_paragraph_latest_state":"..."}。
        """;
    public static final String FINAL_REPORT_SYSTEM = """
        你是 QueryEngine 的报告整合器。
        你要将所有段落整合为一份完整报告。
        输出 JSON 对象：{"final_report":"..."}。
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
