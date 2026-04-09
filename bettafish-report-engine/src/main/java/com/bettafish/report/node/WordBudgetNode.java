package com.bettafish.report.node;

import java.util.List;
import java.util.stream.Collectors;

import com.bettafish.common.llm.LlmGateway;
import com.bettafish.report.prompt.ReportPrompts;
import org.springframework.stereotype.Component;

@Component
public class WordBudgetNode {

    static final String REPORT_CLIENT = "reportChatClient";
    static final int DEFAULT_TOTAL_WORDS = 40000;

    private final LlmGateway llmGateway;

    public WordBudgetNode(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public record ChapterBudget(String chapterId, String title,
                                int targetWords, int minWords, int maxWords) {}

    public record WordBudgetResult(int totalWords, List<ChapterBudget> chapters) {}

    public WordBudgetResult plan(String query, String templateName,
                                 List<DocumentLayoutNode.TocEntry> tocPlan,
                                 String materialSummary) {
        String userPrompt = buildUserPrompt(query, templateName, tocPlan, materialSummary);
        return llmGateway.callJson(
            REPORT_CLIENT,
            ReportPrompts.WORD_BUDGET_SYSTEM,
            userPrompt,
            WordBudgetResult.class,
            () -> defaultBudget(tocPlan)
        );
    }

    private String buildUserPrompt(String query, String templateName,
                                   List<DocumentLayoutNode.TocEntry> tocPlan,
                                   String materialSummary) {
        String tocSection = tocPlan.stream()
            .map(e -> "- " + e.chapterId() + ": " + e.display())
            .collect(Collectors.joining("\n"));
        return "查询主题：" + query
            + "\n选定模板：" + templateName
            + "\n目录规划：\n" + tocSection
            + "\n素材摘要：" + materialSummary;
    }

    private WordBudgetResult defaultBudget(List<DocumentLayoutNode.TocEntry> tocPlan) {
        int perChapter = tocPlan.isEmpty() ? DEFAULT_TOTAL_WORDS : DEFAULT_TOTAL_WORDS / tocPlan.size();
        List<ChapterBudget> chapters = tocPlan.stream()
            .map(e -> new ChapterBudget(
                e.chapterId(),
                e.display(),
                perChapter,
                (int) (perChapter * 0.8),
                (int) (perChapter * 1.2)))
            .toList();
        return new WordBudgetResult(DEFAULT_TOTAL_WORDS, chapters);
    }
}
