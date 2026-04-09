package com.bettafish.forum.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ForumPromptsTest {

    @Test
    void hostSystemPrompt_containsRoleDefinition() {
        assertThat(ForumPrompts.HOST_SYSTEM_PROMPT)
            .contains("事件梳理")
            .contains("纠正错误")
            .contains("整合观点")
            .contains("趋势预测");
    }

    @Test
    void hostSystemPrompt_containsAgentDescriptions() {
        assertThat(ForumPrompts.HOST_SYSTEM_PROMPT)
            .contains("INSIGHT")
            .contains("MEDIA")
            .contains("QUERY");
    }

    @Test
    void hostSystemPrompt_containsJsonOutputFormat() {
        assertThat(ForumPrompts.HOST_SYSTEM_PROMPT)
            .contains("revision")
            .contains("focusPoints")
            .contains("challengeQuestions")
            .contains("evidenceGaps");
    }

    @Test
    void buildGuidanceUserPrompt_containsStructuredSections() {
        var transcript = java.util.List.of(
            new com.bettafish.common.model.ForumMessage("agent", "INSIGHT", "测试内容")
        );
        var result = ForumPrompts.buildGuidanceUserPrompt(transcript, 1);
        assertThat(result)
            .contains("事件梳理")
            .contains("观点整合")
            .contains("深层次分析")
            .contains("问题引导");
    }
}
