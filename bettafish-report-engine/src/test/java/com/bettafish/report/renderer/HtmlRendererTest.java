package com.bettafish.report.renderer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;

class HtmlRendererTest {

    private final HtmlRenderer renderer = new HtmlRenderer();

    private DocumentIr doc(DocumentBlock... blocks) {
        return new DocumentIr(
            new DocumentMeta("Test Report", "Summary", "query", "default", Instant.now()),
            List.of(blocks)
        );
    }

    @Test
    void rendersAllSupportedDocumentIrBlocks() {
        DocumentIr document = new DocumentIr(
            new DocumentMeta(
                "BettaFish analysis report",
                "Report <summary>",
                "武汉大学樱花季舆情热度",
                "default",
                Instant.parse("2026-03-19T00:00:00Z")
            ),
            List.of(
                new DocumentBlock.HeadingBlock(2, "Overview"),
                new DocumentBlock.ParagraphBlock("Paragraph <content>."),
                new DocumentBlock.ListBlock(false, List.of("Item A", "Item B")),
                new DocumentBlock.QuoteBlock("Quoted insight", "ForumHost"),
                new DocumentBlock.TableBlock(List.of("Platform", "Heat"), List.of(List.of("Weibo", "High"))),
                new DocumentBlock.CodeBlock("json", "{\"ok\":true}"),
                new DocumentBlock.LinkBlock("Source link", "https://example.com/source")
            )
        );

        String html = renderer.render(document);

        assertTrue(html.contains("<h1>BettaFish analysis report</h1>"));
        assertTrue(html.contains("Report &lt;summary&gt;"));
        assertTrue(html.contains("<h2"));
        assertTrue(html.contains("Overview"));
        assertTrue(html.contains("Paragraph &lt;content&gt;."));
        assertTrue(html.contains("<ul><li>Item A</li><li>Item B</li></ul>"));
        assertTrue(html.contains("<blockquote>"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<pre><code class=\"language-json\">"));
        assertTrue(html.contains("<a href=\"https://example.com/source\">Source link</a>"));
    }

    @Test
    void rendersWidgetBlock_containsChartJs() {
        String html = renderer.render(doc(
            new DocumentBlock.WidgetBlock("bar", Map.of("labels", List.of("A", "B"), "data", List.of(1, 2)))
        ));
        assertTrue(html.contains("chart-container"));
        assertTrue(html.contains("<canvas id=\"chart-0\""));
        assertTrue(html.contains("new Chart"));
    }

    @Test
    void rendersCalloutBlock_containsLevel() {
        String html = renderer.render(doc(new DocumentBlock.CalloutBlock("warn", "Be careful")));
        assertTrue(html.contains("callout-warn"));
        assertTrue(html.contains("Be careful"));
    }

    @Test
    void rendersKpiGrid_containsCards() {
        String html = renderer.render(doc(new DocumentBlock.KpiGridBlock(List.of(
            new DocumentBlock.KpiItem("Views", "10K", "+5%"),
            new DocumentBlock.KpiItem("Likes", "2K", "+3%")
        ))));
        assertTrue(html.contains("kpi-grid"));
        assertTrue(html.contains("kpi-card"));
        assertTrue(html.contains("10K"));
    }

    @Test
    void rendersHrBlock() {
        String html = renderer.render(doc(new DocumentBlock.HrBlock()));
        assertTrue(html.contains("<hr>"));
    }

    @Test
    void rendersSwotTable_containsFourQuadrants() {
        String html = renderer.render(doc(new DocumentBlock.SwotTableBlock("SWOT",
            List.of("S1"), List.of("W1"), List.of("O1"), List.of("T1"))));
        assertTrue(html.contains("swot-grid"));
        assertTrue(html.contains("swot-s"));
        assertTrue(html.contains("swot-w"));
        assertTrue(html.contains("swot-o"));
        assertTrue(html.contains("swot-t"));
    }

    @Test
    void rendersPestTable_containsFourQuadrants() {
        String html = renderer.render(doc(new DocumentBlock.PestTableBlock("PEST",
            List.of("P1"), List.of("E1"), List.of("S1"), List.of("T1"))));
        assertTrue(html.contains("pest-grid"));
        assertTrue(html.contains("pest-p"));
        assertTrue(html.contains("pest-e"));
        assertTrue(html.contains("pest-s"));
        assertTrue(html.contains("pest-t"));
    }

    @Test
    void rendersEngineQuote_containsEngineName() {
        String html = renderer.render(doc(new DocumentBlock.EngineQuoteBlock("insight", "Title",
            List.of(new DocumentBlock.ParagraphBlock("inner text")))));
        assertTrue(html.contains("engine-quote"));
        assertTrue(html.contains("engine-insight"));
        assertTrue(html.contains("insight"));
    }

    @Test
    void rendersMathBlock_containsLatex() {
        String html = renderer.render(doc(new DocumentBlock.MathBlock("E=mc^2")));
        assertTrue(html.contains("math-block"));
        assertTrue(html.contains("$$E=mc^2$$"));
    }

    @Test
    void rendersToc_containsHeadingLinks() {
        String html = renderer.render(doc(
            new DocumentBlock.HeadingBlock(2, "Section One"),
            new DocumentBlock.HeadingBlock(3, "Sub Section")
        ));
        assertTrue(html.contains("class=\"toc\""));
        assertTrue(html.contains("heading-0"));
        assertTrue(html.contains("Section One"));
    }

    @Test
    void includesChartJsCdn() {
        String html = renderer.render(doc(new DocumentBlock.ParagraphBlock("test")));
        assertTrue(html.contains("https://cdn.jsdelivr.net/npm/chart.js"));
    }

    @Test
    void includesMathJaxCdn() {
        String html = renderer.render(doc(new DocumentBlock.ParagraphBlock("test")));
        assertTrue(html.contains("https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"));
    }

    @Test
    void includesDarkModeToggle() {
        String html = renderer.render(doc(new DocumentBlock.ParagraphBlock("test")));
        assertTrue(html.contains("theme-toggle"));
        assertTrue(html.contains("data-theme"));
    }
}