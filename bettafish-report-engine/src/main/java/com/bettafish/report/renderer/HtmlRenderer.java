package com.bettafish.report.renderer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.ReportSection;

@Component
public class HtmlRenderer {

    private final String css;
    private final List<String> chartScripts = new ArrayList<>();

    public HtmlRenderer() {
        try (var is = getClass().getResourceAsStream("/report/styles.css")) {
            this.css = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String render(DocumentIr documentIr) {
        chartScripts.clear();
        headingCounter = 0;
        StringBuilder html = new StringBuilder();
        html.append(renderHead(documentIr.meta()));
        html.append("<body>\n");
        html.append(renderThemeToggle());
        html.append(renderToc(documentIr.blocks()));
        html.append("<main>\n");
        html.append("<h1>").append(escapeHtml(documentIr.meta().title())).append("</h1>\n");
        html.append("<p>").append(escapeHtml(documentIr.meta().summary())).append("</p>\n");
        int chartIndex = 0;
        for (DocumentBlock block : documentIr.blocks()) {
            chartIndex = renderBlock(html, block, chartIndex);
        }
        html.append("</main>\n");
        html.append(renderScripts());
        html.append("</body>\n</html>");
        return html.toString();
    }

    public String render(String title, String summary, List<ReportSection> sections) {
        return render(new com.bettafish.common.api.ReportDocument(title, summary, sections, "").documentIr());
    }

    private String renderHead(DocumentMeta meta) {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>%s</title>
            <style>%s</style>
            <script src="https://cdn.jsdelivr.net/npm/chart.js" defer></script>
            <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js" defer></script>
            </head>
            """.formatted(escapeHtml(meta.title()), css);
    }

    private String renderThemeToggle() {
        return """
            <button class="theme-toggle" onclick="document.documentElement.setAttribute(\
            'data-theme',document.documentElement.getAttribute('data-theme')==='dark'?'light':'dark'\
            )">&#9789;</button>
            """;
    }

    private String renderToc(List<DocumentBlock> blocks) {
        StringBuilder toc = new StringBuilder("<nav class=\"toc\"><strong>目录</strong><ul>\n");
        int headingCount = 0;
        for (DocumentBlock block : blocks) {
            if (block instanceof DocumentBlock.HeadingBlock h && h.level() <= 3) {
                toc.append("<li><a href=\"#heading-").append(headingCount)
                   .append("\">").append(escapeHtml(h.text())).append("</a></li>\n");
                headingCount++;
            }
        }
        toc.append("</ul></nav>\n");
        return headingCount > 0 ? toc.toString() : "";
    }

    private int headingCounter = 0;

    private int renderBlock(StringBuilder html, DocumentBlock block, int chartIndex) {
        switch (block) {
            case DocumentBlock.HeadingBlock h -> {
                int lvl = Math.min(6, Math.max(2, h.level()));
                html.append("<h").append(lvl).append(" id=\"heading-").append(headingCounter++).append("\">")
                    .append(escapeHtml(h.text())).append("</h").append(lvl).append(">\n");
            }
            case DocumentBlock.ParagraphBlock p ->
                html.append("<p>").append(escapeHtml(p.text())).append("</p>\n");
            case DocumentBlock.ListBlock l -> {
                String tag = l.ordered() ? "ol" : "ul";
                html.append("<").append(tag).append(">");
                for (String item : l.items()) html.append("<li>").append(escapeHtml(item)).append("</li>");
                html.append("</").append(tag).append(">\n");
            }
            case DocumentBlock.QuoteBlock q -> {
                html.append("<blockquote><p>").append(escapeHtml(q.text())).append("</p>");
                if (q.attribution() != null && !q.attribution().isBlank())
                    html.append("<footer>").append(escapeHtml(q.attribution())).append("</footer>");
                html.append("</blockquote>\n");
            }
            case DocumentBlock.TableBlock t -> {
                html.append("<table><thead><tr>");
                for (String h : t.headers()) html.append("<th>").append(escapeHtml(h)).append("</th>");
                html.append("</tr></thead><tbody>");
                for (List<String> row : t.rows()) {
                    html.append("<tr>");
                    for (String c : row) html.append("<td>").append(escapeHtml(c)).append("</td>");
                    html.append("</tr>");
                }
                html.append("</tbody></table>\n");
            }
            case DocumentBlock.CodeBlock c ->
                html.append("<pre><code class=\"language-").append(escapeAttr(c.language())).append("\">")
                    .append(escapeHtml(c.code())).append("</code></pre>\n");
            case DocumentBlock.LinkBlock l ->
                html.append("<p><a href=\"").append(escapeAttr(l.href())).append("\">")
                    .append(escapeHtml(l.text())).append("</a></p>\n");
            case DocumentBlock.WidgetBlock w -> {
                String canvasId = "chart-" + chartIndex;
                html.append("<div class=\"chart-container\"><canvas id=\"").append(canvasId).append("\"></canvas></div>\n");
                chartScripts.add(buildChartScript(canvasId, w.chartType(), w.config()));
                chartIndex++;
            }
            case DocumentBlock.CalloutBlock c ->
                html.append("<div class=\"callout callout-").append(escapeAttr(c.level())).append("\">")
                    .append(escapeHtml(c.text())).append("</div>\n");
            case DocumentBlock.KpiGridBlock k -> {
                html.append("<div class=\"kpi-grid\">");
                for (DocumentBlock.KpiItem item : k.items()) {
                    html.append("<div class=\"kpi-card\"><span class=\"kpi-label\">").append(escapeHtml(item.label()))
                        .append("</span><span class=\"kpi-value\">").append(escapeHtml(item.value()))
                        .append("</span><span class=\"kpi-trend\">").append(escapeHtml(item.trend()))
                        .append("</span></div>");
                }
                html.append("</div>\n");
            }
            case DocumentBlock.HrBlock ignored -> html.append("<hr>\n");
            case DocumentBlock.SwotTableBlock s -> {
                html.append("<div class=\"swot-grid\">");
                html.append("<div class=\"swot-cell swot-s\"><strong>Strengths</strong><ul>");
                for (String i : s.strengths()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div>");
                html.append("<div class=\"swot-cell swot-w\"><strong>Weaknesses</strong><ul>");
                for (String i : s.weaknesses()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div>");
                html.append("<div class=\"swot-cell swot-o\"><strong>Opportunities</strong><ul>");
                for (String i : s.opportunities()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div>");
                html.append("<div class=\"swot-cell swot-t\"><strong>Threats</strong><ul>");
                for (String i : s.threats()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div></div>\n");
            }
            case DocumentBlock.PestTableBlock p -> {
                html.append("<div class=\"pest-grid\">");
                html.append("<div class=\"pest-cell pest-p\"><strong>Political</strong><ul>");
                for (String i : p.political()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div>");
                html.append("<div class=\"pest-cell pest-e\"><strong>Economic</strong><ul>");
                for (String i : p.economic()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div>");
                html.append("<div class=\"pest-cell pest-s\"><strong>Social</strong><ul>");
                for (String i : p.social()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div>");
                html.append("<div class=\"pest-cell pest-t\"><strong>Technological</strong><ul>");
                for (String i : p.technological()) html.append("<li>").append(escapeHtml(i)).append("</li>");
                html.append("</ul></div></div>\n");
            }
            case DocumentBlock.EngineQuoteBlock eq -> {
                html.append("<blockquote class=\"engine-quote engine-").append(escapeAttr(eq.engine()))
                    .append("\"><p><strong>").append(escapeHtml(eq.engine()))
                    .append("</strong>: ").append(escapeHtml(eq.title())).append("</p>");
                for (DocumentBlock inner : eq.blocks()) chartIndex = renderBlock(html, inner, chartIndex);
                html.append("</blockquote>\n");
            }
            case DocumentBlock.MathBlock m ->
                html.append("<div class=\"math-block\">$$").append(escapeHtml(m.latex())).append("$$</div>\n");
        }
        return chartIndex;
    }

    private String buildChartScript(String canvasId, String chartType, Map<String, Object> config) {
        StringBuilder sb = new StringBuilder();
        sb.append("new Chart(document.getElementById('").append(canvasId).append("'),{type:'")
          .append(chartType != null ? chartType : "bar").append("',data:{");
        if (config != null) {
            Object labels = config.get("labels");
            Object data = config.get("data");
            if (labels != null) sb.append("labels:").append(toJsonArray(labels)).append(",");
            sb.append("datasets:[{label:'data',data:").append(data != null ? toJsonArray(data) : "[]").append("}]");
        } else {
            sb.append("labels:[],datasets:[{label:'data',data:[]}]");
        }
        sb.append("}});");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String toJsonArray(Object obj) {
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                Object v = list.get(i);
                if (v instanceof Number) sb.append(v);
                else sb.append("'").append(v).append("'");
            }
            return sb.append("]").toString();
        }
        return "[]";
    }

    private String renderScripts() {
        if (chartScripts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<script>window.addEventListener('load',function(){");
        for (String script : chartScripts) sb.append(script);
        sb.append("});</script>\n");
        return sb.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                     .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeAttr(String value) {
        return escapeHtml(value);
    }
}