package com.bettafish.insight.keyword;

import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.insight.prompt.InsightPrompts;
import org.springframework.stereotype.Service;

@Service
public class KeywordOptimizer {

    static final String KEYWORD_OPTIMIZER_CLIENT = "keywordOptimizerChatClient";

    private final LlmGateway llmGateway;

    public KeywordOptimizer(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public List<String> optimize(String query) {
        return llmGateway.callJson(
            KEYWORD_OPTIMIZER_CLIENT,
            InsightPrompts.KEYWORD_OPTIMIZER_SYSTEM,
            InsightPrompts.buildKeywordOptimizationUserPrompt(query),
            new TypeReference<List<String>>() {
            },
            this::validateKeywords,
            () -> defaultKeywords(query)
        );
    }

    private LlmGateway.ValidationResult validateKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return LlmGateway.ValidationResult.invalid("keywords must not be empty");
        }
        boolean hasBlankKeyword = keywords.stream().anyMatch(keyword -> keyword == null || keyword.isBlank());
        if (hasBlankKeyword) {
            return LlmGateway.ValidationResult.invalid("keywords must not contain blank entries");
        }
        return LlmGateway.ValidationResult.valid();
    }

    private List<String> defaultKeywords(String query) {
        return List.of(query, query + " 评论", query + " 热度");
    }
}
