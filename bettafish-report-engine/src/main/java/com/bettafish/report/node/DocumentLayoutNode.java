package com.bettafish.report.node;

import java.util.List;
import java.util.Map;

import com.bettafish.common.llm.LlmGateway;
import com.bettafish.report.prompt.ReportPrompts;
import org.springframework.stereotype.Component;

@Component
public class DocumentLayoutNode {

    static final String REPORT_CLIENT = "reportChatClient";

    private final LlmGateway llmGateway;

    public DocumentLayoutNode(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public record TocEntry(String chapterId, String display, String description,
                           boolean allowSwot, boolean allowPest) {}

    public record DocumentLayoutResult(String title, String subtitle, String tagline,
                                       List<TocEntry> tocPlan,
                                       Map<String, String> themeTokens) {}

    public DocumentLayoutResult design(String query, String templateName, String materialSummary) {
        String userPrompt = "查询主题：" + query + "\n选定模板：" + templateName + "\n素材摘要：" + materialSummary;
        return llmGateway.callJson(
            REPORT_CLIENT,
            ReportPrompts.DOCUMENT_LAYOUT_SYSTEM,
            userPrompt,
            DocumentLayoutResult.class,
            () -> defaultLayout(query)
        );
    }

    private DocumentLayoutResult defaultLayout(String query) {
        return new DocumentLayoutResult(
            query + " 分析报告",
            "BettaFish 舆情分析系统",
            null,
            List.of(new TocEntry("ch1", "综合分析", "主要分析内容", false, false)),
            Map.of()
        );
    }
}
