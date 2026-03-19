package com.bettafish.report.renderer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;

class HtmlRendererTest {

    @Test
    void rendersAllSupportedDocumentIrBlocks() {
        HtmlRenderer renderer = new HtmlRenderer();
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
        assertTrue(html.contains("<h2>Overview</h2>"));
        assertTrue(html.contains("Paragraph &lt;content&gt;."));
        assertTrue(html.contains("<ul><li>Item A</li><li>Item B</li></ul>"));
        assertTrue(html.contains("<blockquote>"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<pre><code class=\"language-json\">"));
        assertTrue(html.contains("<a href=\"https://example.com/source\">Source link</a>"));
    }
}
