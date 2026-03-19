package com.bettafish.report.renderer;

import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.ReportSection;

@Component
public class HtmlRenderer {

    public String render(DocumentIr document) {
        StringBuilder html = new StringBuilder();
        DocumentMeta meta = document.meta();
        html.append("<html><body>");
        html.append("<h1>").append(escape(meta.title())).append("</h1>");
        html.append("<p>").append(escape(meta.summary())).append("</p>");
        for (DocumentBlock block : document.blocks()) {
            renderBlock(html, block);
        }
        html.append("</body></html>");
        return html.toString();
    }

    public String render(String title, String summary, List<ReportSection> sections) {
        return render(new com.bettafish.common.api.ReportDocument(title, summary, sections, "").documentIr());
    }

    private void renderBlock(StringBuilder html, DocumentBlock block) {
        switch (block) {
            case DocumentBlock.HeadingBlock headingBlock -> html
                .append("<h").append(normalizeLevel(headingBlock.level())).append(">")
                .append(escape(headingBlock.text()))
                .append("</h").append(normalizeLevel(headingBlock.level())).append(">");
            case DocumentBlock.ParagraphBlock paragraphBlock -> html
                .append("<p>").append(escape(paragraphBlock.text())).append("</p>");
            case DocumentBlock.ListBlock listBlock -> renderList(html, listBlock);
            case DocumentBlock.QuoteBlock quoteBlock -> {
                html.append("<blockquote><p>").append(escape(quoteBlock.text())).append("</p>");
                if (quoteBlock.attribution() != null && !quoteBlock.attribution().isBlank()) {
                    html.append("<footer>").append(escape(quoteBlock.attribution())).append("</footer>");
                }
                html.append("</blockquote>");
            }
            case DocumentBlock.TableBlock tableBlock -> renderTable(html, tableBlock);
            case DocumentBlock.CodeBlock codeBlock -> html
                .append("<pre><code class=\"language-")
                .append(escapeAttribute(codeBlock.language()))
                .append("\">")
                .append(escape(codeBlock.code()))
                .append("</code></pre>");
            case DocumentBlock.LinkBlock linkBlock -> html
                .append("<p><a href=\"")
                .append(escapeAttribute(linkBlock.href()))
                .append("\">")
                .append(escape(linkBlock.text()))
                .append("</a></p>");
        }
    }

    private void renderList(StringBuilder html, DocumentBlock.ListBlock listBlock) {
        html.append(listBlock.ordered() ? "<ol>" : "<ul>");
        for (String item : listBlock.items()) {
            html.append("<li>").append(escape(item)).append("</li>");
        }
        html.append(listBlock.ordered() ? "</ol>" : "</ul>");
    }

    private void renderTable(StringBuilder html, DocumentBlock.TableBlock tableBlock) {
        html.append("<table><thead><tr>");
        for (String header : tableBlock.headers()) {
            html.append("<th>").append(escape(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (List<String> row : tableBlock.rows()) {
            html.append("<tr>");
            for (String cell : row) {
                html.append("<td>").append(escape(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
    }

    private int normalizeLevel(int level) {
        return Math.min(6, Math.max(2, level));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private String escapeAttribute(String value) {
        return escape(value).replace("'", "&#39;");
    }
}
