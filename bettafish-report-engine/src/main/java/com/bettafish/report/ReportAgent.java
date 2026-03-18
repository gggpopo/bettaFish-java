package com.bettafish.report;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.api.ReportSection;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.report.prompt.ReportPrompts;
import com.bettafish.report.renderer.HtmlRenderer;
import com.bettafish.report.template.ReportTemplate;

@Service
public class ReportAgent implements ReportGenerator {

    private final HtmlRenderer htmlRenderer;

    public ReportAgent(HtmlRenderer htmlRenderer) {
        this.htmlRenderer = htmlRenderer;
    }

    @Override
    public ReportDocument generate(AnalysisRequest request, ReportInput input) {
        List<ReportSection> sections = new ArrayList<>();
        for (EngineResult result : input.engineResults()) {
            sections.add(new ReportSection(
                result.engineType().name() + " analysis",
                result.summary() + " Key points: " + String.join("; ", result.keyPoints())
            ));
        }
        sections.add(new ReportSection(
            "Forum summary",
            input.forumSummary().overview()
                + " Consensus: "
                + String.join("; ", input.forumSummary().consensusPoints())
        ));
        String title = ReportTemplate.DEFAULT.title();
        String summary = ReportPrompts.REPORT_SYSTEM_PROMPT + " " + request.query();
        return new ReportDocument(title, summary, sections, htmlRenderer.render(title, summary, sections));
    }
}
