package com.bettafish.report.ir;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;

class IrSanitizationTest {

    private final IrValidator validator = new IrValidator();

    private DocumentIr ir(DocumentBlock... blocks) {
        return new DocumentIr(
            new DocumentMeta("Test", "Summary", "query", "template", Instant.now()),
            List.of(blocks)
        );
    }

    @Test
    void rejectsEngineQuoteWithNonParagraphBlocks() {
        var doc = ir(new DocumentBlock.EngineQuoteBlock("insight", "Title",
            List.of(new DocumentBlock.TableBlock(List.of("H"), List.of(List.of("V"))))));
        List<String> errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.contains("EngineQuoteBlock") && e.contains("non-paragraph"));
    }

    @Test
    void rejectsWidgetWithInvalidChartType() {
        var doc = ir(new DocumentBlock.WidgetBlock("unknown", Map.of()));
        List<String> errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.contains("invalid chartType"));
    }

    @Test
    void rejectsSwotWithAllEmptyQuadrants() {
        var doc = ir(new DocumentBlock.SwotTableBlock("Empty SWOT",
            List.of(), List.of(), List.of(), List.of()));
        List<String> errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.contains("all empty quadrants"));
    }

    @Test
    void acceptsValidMixedBlockDocument() {
        var doc = ir(
            new DocumentBlock.HeadingBlock(2, "Title"),
            new DocumentBlock.ParagraphBlock("Text"),
            new DocumentBlock.ListBlock(false, List.of("a", "b")),
            new DocumentBlock.TableBlock(List.of("H1", "H2"), List.of(List.of("V1", "V2"))),
            new DocumentBlock.WidgetBlock("bar", Map.of("type", "bar")),
            new DocumentBlock.CalloutBlock("info", "Info text"),
            new DocumentBlock.KpiGridBlock(List.of(new DocumentBlock.KpiItem("KPI", "100", "up"))),
            new DocumentBlock.HrBlock(),
            new DocumentBlock.SwotTableBlock("SWOT", List.of("S1"), List.of("W1"), List.of("O1"), List.of("T1")),
            new DocumentBlock.PestTableBlock("PEST", List.of("P1"), List.of("E1"), List.of("S1"), List.of("T1")),
            new DocumentBlock.EngineQuoteBlock("insight", "Agent", List.of(new DocumentBlock.ParagraphBlock("Quote"))),
            new DocumentBlock.MathBlock("E=mc^2")
        );
        List<String> errors = validator.validate(doc);
        assertThat(errors).isEmpty();
    }

    @Test
    void rejectsTableWithMismatchedColumns() {
        var doc = ir(new DocumentBlock.TableBlock(
            List.of("H1", "H2"),
            List.of(List.of("V1"))
        ));
        List<String> errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.contains("columns"));
    }

    @Test
    void rejectsHeadingWithInvalidLevel() {
        var doc = ir(
            new DocumentBlock.HeadingBlock(0, "Bad"),
            new DocumentBlock.HeadingBlock(7, "Also bad")
        );
        List<String> errors = validator.validate(doc);
        assertThat(errors).hasSize(2);
    }
}