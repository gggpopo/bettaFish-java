package com.bettafish.report.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;

class HtmlRendererBlockCoverageTest {

    @Test
    void rendersAllFifteenBlockTypes() {
        HtmlRenderer renderer = new HtmlRenderer();
        List<DocumentBlock> blocks = List.of(
            new DocumentBlock.HeadingBlock(1, "Title"),
            new DocumentBlock.ParagraphBlock("Text"),
            new DocumentBlock.ListBlock(false, List.of("a", "b")),
            new DocumentBlock.QuoteBlock("Quote", "Author"),
            new DocumentBlock.TableBlock(List.of("H1"), List.of(List.of("V1"))),
            new DocumentBlock.CodeBlock("java", "code"),
            new DocumentBlock.LinkBlock("Link", "https://example.com"),
            new DocumentBlock.WidgetBlock("bar", Map.of("type", "bar")),
            new DocumentBlock.CalloutBlock("info", "Info text"),
            new DocumentBlock.KpiGridBlock(List.of(new DocumentBlock.KpiItem("KPI", "100", "up"))),
            new DocumentBlock.HrBlock(),
            new DocumentBlock.SwotTableBlock("SWOT", List.of("S1"), List.of("W1"), List.of("O1"), List.of("T1")),
            new DocumentBlock.PestTableBlock("PEST", List.of("P1"), List.of("E1"), List.of("S1"), List.of("T1")),
            new DocumentBlock.EngineQuoteBlock("insight", "Insight Agent",
                List.of(new DocumentBlock.ParagraphBlock("Quote content"))),
            new DocumentBlock.MathBlock("E=mc^2")
        );
        DocumentIr ir = new DocumentIr(
            new DocumentMeta("Test", "Summary", "query", "template", Instant.now()),
            blocks
        );

        String html = renderer.render(ir);

        assertThat(html).contains("<h2");
        assertThat(html).contains("<p>Text</p>");
        assertThat(html).contains("<ul>");
        assertThat(html).contains("<blockquote>");
        assertThat(html).contains("<table>");
        assertThat(html).contains("<pre><code");
        assertThat(html).contains("href=\"https://example.com\"");
        assertThat(html).contains("chart-");
        assertThat(html).contains("callout-info");
        assertThat(html).contains("kpi-grid");
        assertThat(html).contains("<hr>");
        assertThat(html).contains("swot");
        assertThat(html).contains("pest");
        assertThat(html).contains("engine-quote");
        assertThat(html).contains("E=mc^2");
    }
}
