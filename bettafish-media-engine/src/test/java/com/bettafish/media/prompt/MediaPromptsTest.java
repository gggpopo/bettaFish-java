package com.bettafish.media.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MediaPromptsTest {

    @Test
    void firstSearchSystem_containsMultimodalTools() {
        assertThat(MediaPrompts.FIRST_SEARCH_SYSTEM)
            .contains("comprehensive_search")
            .contains("web_search_only")
            .contains("search_for_structured_data")
            .contains("search_last_24_hours")
            .contains("search_last_week");
    }

    @Test
    void firstSummarySystem_containsMultimodalAnalysis() {
        assertThat(MediaPrompts.FIRST_SUMMARY_SYSTEM)
            .contains("多模态")
            .contains("800");
    }

    @Test
    void reflectionDecision_containsToolsAndShouldRefine() {
        assertThat(MediaPrompts.REFLECTION_DECISION_SYSTEM)
            .contains("comprehensive_search")
            .contains("should_refine");
    }

    @Test
    void finalReportSystem_exists() {
        assertThat(MediaPrompts.FINAL_REPORT_SYSTEM)
            .contains("万字")
            .contains("全景");
    }
}
