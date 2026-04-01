package com.bettafish.media.prompt;

import java.util.List;
import java.util.stream.Collectors;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.model.ForumGuidance;

public final class MediaPrompts {

    public static final String FIRST_SEARCH_SYSTEM = "MediaAgent summarized visual and web coverage signals for";
    public static final String FIRST_SUMMARY_SYSTEM = """
        你是 MediaEngine 的首轮总结器。
        你需要基于多模态搜索结果总结传播画面、平台分布和内容形态。
        """;
    public static final String REFLECTION_DECISION_SYSTEM = """
        你是 MediaEngine 的反思决策器。
        你需要判断当前多模态总结是否还要继续补充检索。
        输出 JSON 对象：{"should_refine":true|false,"search_query":"...","reasoning":"..."}。
        """;
    public static final String REFLECTION_SUMMARY_SYSTEM = """
        你是 MediaEngine 的反思总结器。
        你需要结合论坛主持指导补齐证据缺口，并输出更新后的多模态研判。
        """;

    private MediaPrompts() {
    }

    public static String buildFirstSummaryUserPrompt(String query, List<SourceReference> sources, ForumGuidance forumGuidance) {
        return """
            主题：%s
            论坛主持指导：
            %s
            多模态来源证据：
            %s
            请输出一段多模态传播总结，重点覆盖内容形态、平台分布和视觉传播特征。
            """.formatted(
            query,
            renderForumGuidance(forumGuidance),
            renderSources(sources)
        );
    }

    public static String buildReflectionSummaryUserPrompt(String query, String currentSummary,
                                                          List<SourceReference> sources,
                                                          ForumGuidance forumGuidance) {
        return """
            主题：%s
            论坛主持指导：
            %s
            当前总结：
            %s
            多模态来源证据：
            %s
            请根据主持指导修正或扩写总结，优先补齐证据缺口并指出新的传播观察。
            """.formatted(
            query,
            renderForumGuidance(forumGuidance),
            currentSummary,
            renderSources(sources)
        );
    }

    public static String buildReflectionDecisionUserPrompt(String query, String currentSummary, List<SourceReference> sources,
                                                           ForumGuidance forumGuidance, int remainingReflections) {
        return """
            主题：%s
            论坛主持指导：
            %s
            当前总结：
            %s
            已有来源证据：
            %s
            剩余可用反思轮数：%s
            请判断是否还需要补充检索，并返回结构化 JSON。
            """.formatted(
            query,
            renderForumGuidance(forumGuidance),
            currentSummary,
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
            return "暂无检索结果。";
        }
        return sources.stream()
            .map(source -> "- %s | %s | %s".formatted(source.title(), source.url(), source.snippet()))
            .collect(Collectors.joining("\n"));
    }
}
