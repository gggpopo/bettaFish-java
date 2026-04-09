package com.bettafish.common.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IrValidatorTest {

    @Test
    void validDocument_returnsNoErrors() {
        var ir = new DocumentIr(
                new DocumentMeta("title", "summary", "query", "template", Instant.now()),
                List.of(
                        new DocumentBlock.HeadingBlock(1, "Hello"),
                        new DocumentBlock.ParagraphBlock("World"),
                        new DocumentBlock.HrBlock()
                )
        );
        assertThat(IrValidator.validate(ir)).isEmpty();
    }

    @Test
    void headingBlock_invalidLevel_returnsError() {
        var errors = IrValidator.validateBlocks(List.of(
                new DocumentBlock.HeadingBlock(0, "Bad"),
                new DocumentBlock.HeadingBlock(7, "Also bad")
        ));
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).path()).isEqualTo("blocks[0]");
        assertThat(errors.get(0).message()).contains("level");
        assertThat(errors.get(1).path()).isEqualTo("blocks[1]");
    }

    @Test
    void widgetBlock_invalidChartType_returnsError() {
        var errors = IrValidator.validateBlocks(List.of(
                new DocumentBlock.WidgetBlock("unknown", Map.of("k", "v"))
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("blocks[0]");
        assertThat(errors.get(0).message()).contains("chartType");
    }

    @Test
    void calloutBlock_invalidLevel_returnsError() {
        var errors = IrValidator.validateBlocks(List.of(
                new DocumentBlock.CalloutBlock("critical", "text")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("blocks[0]");
        assertThat(errors.get(0).message()).contains("callout level");
    }

    @Test
    void engineQuoteBlock_nonParagraphInner_returnsError() {
        var errors = IrValidator.validateBlocks(List.of(
                new DocumentBlock.EngineQuoteBlock("query", "title", List.of(
                        new DocumentBlock.HeadingBlock(1, "Not a paragraph")
                ))
        ));
        assertThat(errors).anyMatch(e ->
                e.path().equals("blocks[0].blocks[0]") && e.message().contains("ParagraphBlock"));
    }

    @Test
    void swotTableBlock_allEmpty_returnsError() {
        var errors = IrValidator.validateBlocks(List.of(
                new DocumentBlock.SwotTableBlock("SWOT", List.of(), List.of(), List.of(), List.of())
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("non-empty quadrant");
    }

    @Test
    void tableBlock_rowColumnMismatch_returnsError() {
        var errors = IrValidator.validateBlocks(List.of(
                new DocumentBlock.TableBlock(
                        List.of("A", "B", "C"),
                        List.of(List.of("1", "2"))
                )
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path()).isEqualTo("blocks[0].rows[0]");
        assertThat(errors.get(0).message()).contains("row length");
    }

    @Test
    void emptyBlocks_returnsNoErrors() {
        var errors = IrValidator.validateBlocks(List.of());
        assertThat(errors).isEmpty();
    }
}
