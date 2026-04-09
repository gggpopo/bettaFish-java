package com.bettafish.insight.keyword;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.insight.prompt.InsightPrompts;
import org.springframework.stereotype.Service;

@Service
public class KeywordOptimizer {

    static final String KEYWORD_OPTIMIZER_CLIENT = "keywordOptimizerChatClient";
    static final int MAX_KEYWORD_LENGTH = 20;

    private static final Set<String> BAD_KEYWORDS = Set.of(
        "态度分析", "未来展望", "舆情传播", "公众反应", "社会影响",
        "情绪倾向", "民众情绪", "舆论导向", "传播路径", "影响评估",
        "趋势研判", "舆情监测", "民意调查"
    );

    private final LlmGateway llmGateway;

    public KeywordOptimizer(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public List<String> optimize(String query) {
        List<String> keywords = llmGateway.callJson(
            KEYWORD_OPTIMIZER_CLIENT,
            InsightPrompts.KEYWORD_OPTIMIZER_SYSTEM,
            InsightPrompts.buildKeywordOptimizationUserPrompt(query),
            new TypeReference<List<String>>() {
            },
            this::validateKeywords,
            () -> defaultKeywords(query)
        );
        return filterAndClean(keywords);
    }

    public List<String> optimize(String query, String context) {
        String userPrompt = InsightPrompts.buildKeywordOptimizationUserPrompt(query)
            + "\n\n上下文信息：" + context;
        List<String> keywords = llmGateway.callJson(
            KEYWORD_OPTIMIZER_CLIENT,
            InsightPrompts.KEYWORD_OPTIMIZER_SYSTEM,
            userPrompt,
            new TypeReference<List<String>>() {
            },
            this::validateKeywords,
            () -> defaultKeywords(query)
        );
        return filterAndClean(keywords);
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

    private List<String> filterAndClean(List<String> keywords) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (BAD_KEYWORDS.contains(keyword)) {
                continue;
            }
            String cleaned = keyword.length() > MAX_KEYWORD_LENGTH
                ? keyword.substring(0, MAX_KEYWORD_LENGTH)
                : keyword;
            seen.add(cleaned);
        }
        return new ArrayList<>(seen);
    }

    private List<String> defaultKeywords(String query) {
        return List.of(query, query + " 评论", query + " 热度");
    }
}
