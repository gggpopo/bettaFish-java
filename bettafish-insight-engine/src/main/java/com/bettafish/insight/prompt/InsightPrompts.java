package com.bettafish.insight.prompt;

import java.util.List;
import java.util.stream.Collectors;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.insight.SentimentSignal;

public final class InsightPrompts {

    public static final String FIRST_SEARCH_SYSTEM = "InsightAgent mines social discussion and sentiment signals.";
    public static final String FIRST_SUMMARY_SYSTEM = """
        你是 InsightEngine 的首轮总结器。
        你需要基于情感信号、关键词和数据库检索结果总结社交舆情观察。
        """;
    public static final String REFLECTION_DECISION_SYSTEM = """
        你是 InsightEngine 的反思决策器。
        你需要判断当前社交舆情总结是否还要继续补充检索。
        输出 JSON 对象：{"should_refine":true|false,"search_query":"...","reasoning":"..."}。
        """;
    public static final String REFLECTION_SUMMARY_SYSTEM = """
        你是 InsightEngine 的反思总结器。
        你需要结合论坛主持指导补齐群体差异、争议点和证据缺口。
        """;
    public static final String KEYWORD_OPTIMIZER_SYSTEM = """
        你是 InsightEngine 的检索词优化器。
        请基于原始查询生成一组适合社交舆情检索的关键词，覆盖主题本体、评论反馈、热度趋势。
        """;

    private InsightPrompts() {
    }

    public static String buildKeywordOptimizationUserPrompt(String query) {
        return """
            原始查询：%s

            请返回 3 到 5 个用于社交媒体和舆情数据库检索的关键词短语。
            要求：
            1. 第一个关键词必须保留原始主题。
            2. 其余关键词优先覆盖评论反馈、传播热度、争议话题。
            3. 只返回字符串数组。
            """.formatted(query);
    }

    public static String buildFirstSummaryUserPrompt(String query, SentimentSignal sentiment, List<String> optimizedKeywords,
                                                     List<SourceReference> sources, ForumGuidance forumGuidance) {
        return """
            主题：%s
            论坛主持指导：
            %s
            情感信号：%s（置信度 %.2f）
            检索关键词：%s
            数据库证据：
            %s
            请输出一段社交舆情总结，覆盖主要情绪、讨论焦点和代表性样本。
            """.formatted(
            query,
            renderForumGuidance(forumGuidance),
            sentiment.label(),
            sentiment.confidence(),
            String.join("、", optimizedKeywords),
            renderSources(sources)
        );
    }

    public static String buildReflectionSummaryUserPrompt(String query, String currentSummary, SentimentSignal sentiment,
                                                          List<String> optimizedKeywords, List<SourceReference> sources,
                                                          ForumGuidance forumGuidance) {
        return """
            主题：%s
            论坛主持指导：
            %s
            当前总结：
            %s
            情感信号：%s（置信度 %.2f）
            检索关键词：%s
            数据库证据：
            %s
            请根据主持指导修正总结，优先解释群体差异、争议来源和证据缺口。
            """.formatted(
            query,
            renderForumGuidance(forumGuidance),
            currentSummary,
            sentiment.label(),
            sentiment.confidence(),
            String.join("、", optimizedKeywords),
            renderSources(sources)
        );
    }

    public static String buildReflectionDecisionUserPrompt(String query, String currentSummary, SentimentSignal sentiment,
                                                           List<String> optimizedKeywords, List<SourceReference> sources,
                                                           ForumGuidance forumGuidance, int remainingReflections) {
        return """
            主题：%s
            论坛主持指导：
            %s
            当前总结：
            %s
            情感信号：%s（置信度 %.2f）
            检索关键词：%s
            数据库证据：
            %s
            剩余可用反思轮数：%s
            请判断是否还需要补充检索，并返回结构化 JSON。
            """.formatted(
            query,
            renderForumGuidance(forumGuidance),
            currentSummary,
            sentiment.label(),
            sentiment.confidence(),
            String.join("、", optimizedKeywords),
            renderSources(sources),
            remainingReflections
        );
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

    private static String renderSources(List<SourceReference> sources) {
        if (sources == null || sources.isEmpty()) {
            return "暂无数据库命中结果。";
        }
        return sources.stream()
            .map(source -> "- %s | %s | %s".formatted(source.title(), source.url(), source.snippet()))
            .collect(Collectors.joining("\n"));
    }
}
