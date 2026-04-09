package com.bettafish.report.prompt;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReportPromptsTest {

    @Test
    void chapterGenerationSystem_containsBlockTypes() {
        assertThat(ReportPrompts.CHAPTER_GENERATION_SYSTEM_PROMPT)
            .contains("heading")
            .contains("paragraph")
            .contains("list")
            .contains("table")
            .contains("widget");
    }

    @Test
    void chapterGenerationSystem_containsSwotPestRules() {
        assertThat(ReportPrompts.CHAPTER_GENERATION_SYSTEM_PROMPT)
            .contains("SWOT")
            .contains("PEST");
    }

    @Test
    void templateSelectionSystem_exists() {
        assertThat(ReportPrompts.TEMPLATE_SELECTION_SYSTEM)
            .contains("模板")
            .contains("template_name");
    }

    @Test
    void documentLayoutSystem_exists() {
        assertThat(ReportPrompts.DOCUMENT_LAYOUT_SYSTEM)
            .contains("标题")
            .contains("目录");
    }

    @Test
    void wordBudgetSystem_exists() {
        assertThat(ReportPrompts.WORD_BUDGET_SYSTEM)
            .contains("字数")
            .contains("40000");
    }

    @Test
    void chapterRepairSystem_exists() {
        assertThat(ReportPrompts.CHAPTER_REPAIR_SYSTEM)
            .contains("修复");
    }

    @Test
    void chapterRecoverySystem_exists() {
        assertThat(ReportPrompts.CHAPTER_RECOVERY_SYSTEM)
            .contains("抢修");
    }

    @Test
    void buildChapterGenerationUserPrompt_preservesSignature() {
        var spec = new com.bettafish.report.ir.ChapterSpec("ch01", "测试标题", "测试目标", "测试素材");
        var result = ReportPrompts.buildChapterGenerationUserPrompt("测试主题", spec, 1, null);
        assertThat(result).contains("测试主题").contains("测试标题");
    }
}
