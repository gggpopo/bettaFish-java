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
