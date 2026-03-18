package com.bettafish.report.renderer;

import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.ReportSection;

@Component
public class HtmlRenderer {

    public String render(String title, String summary, List<ReportSection> sections) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h1>").append(title).append("</h1>");
        html.append("<p>").append(summary).append("</p>");
        for (ReportSection section : sections) {
            html.append("<section><h2>")
                .append(section.title())
                .append("</h2><p>")
                .append(section.content())
                .append("</p></section>");
        }
        html.append("</body></html>");
        return html.toString();
    }
}
