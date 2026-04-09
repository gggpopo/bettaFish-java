package com.bettafish.report.node;

import com.bettafish.common.llm.LlmGateway;
import com.bettafish.report.prompt.ReportPrompts;
import org.springframework.stereotype.Component;

@Component
public class TemplateSelectionNode {

    static final String REPORT_CLIENT = "reportChatClient";

    private final LlmGateway llmGateway;

    public TemplateSelectionNode(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public record TemplateSelectionResult(String templateName, String selectionReason) {}

    public TemplateSelectionResult select(String query, String materialSummary) {
        String userPrompt = "查询主题：" + query + "\n素材摘要：" + materialSummary;
        return llmGateway.callJson(
            REPORT_CLIENT,
            ReportPrompts.TEMPLATE_SELECTION_SYSTEM,
            userPrompt,
            TemplateSelectionResult.class,
            () -> new TemplateSelectionResult("社会公共热点事件分析报告模板", "默认模板")
        );
    }
}
