package com.bettafish.insight.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InsightPromptsTest {

    @Test
    void firstSearchSystem_containsSentimentTools() {
        assertThat(InsightPrompts.FIRST_SEARCH_SYSTEM)
            .contains("search_hot_content")
            .contains("search_topic_globally")
            .contains("search_topic_by_date")
            .contains("get_comments_for_topic")
            .contains("search_topic_on_platform")
            .contains("analyze_sentiment");
    }

    @Test
    void firstSearchSystem_containsColloquialGuidance() {
        assertThat(InsightPrompts.FIRST_SEARCH_SYSTEM)
            .contains("接地气");
    }

    @Test
    void firstSummarySystem_containsDataRequirements() {
        assertThat(InsightPrompts.FIRST_SUMMARY_SYSTEM)
            .contains("800")
            .contains("评论");
    }

    @Test
    void reflectionSystem_emphasizesAuthenticVoices() {
        assertThat(InsightPrompts.REFLECTION_DECISION_SYSTEM)
            .contains("search_hot_content")
            .contains("民意");
    }

    @Test
    void reflectionDecision_containsShouldRefine() {
        assertThat(InsightPrompts.REFLECTION_DECISION_SYSTEM)
            .contains("should_refine");
    }

    @Test
    void keywordOptimizerSystem_containsColloquialGuidance() {
        assertThat(InsightPrompts.KEYWORD_OPTIMIZER_SYSTEM)
            .contains("接地气");
    }
}
