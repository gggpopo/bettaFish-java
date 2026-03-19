package com.bettafish.report;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;
import com.bettafish.common.api.DocumentMeta;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.report.ir.ChapterGenerationResult;
import com.bettafish.report.ir.ChapterSpec;
import com.bettafish.report.node.ChapterGenerationNode;
import com.bettafish.report.prompt.ReportPrompts;
import com.bettafish.report.renderer.HtmlRenderer;
import com.bettafish.report.template.ReportTemplate;

@Service
public class ReportAgent implements ReportGenerator {

    private final ChapterGenerationNode chapterGenerationNode;
    private final HtmlRenderer htmlRenderer;

    public ReportAgent(ChapterGenerationNode chapterGenerationNode, HtmlRenderer htmlRenderer) {
        this.chapterGenerationNode = chapterGenerationNode;
        this.htmlRenderer = htmlRenderer;
    }

    @Override
    public ReportDocument generate(AnalysisRequest request, ReportInput input) {
        return generate(request, input, AnalysisEventPublisher.noop());
    }

    @Override
    public ReportDocument generate(AnalysisRequest request, ReportInput input, AnalysisEventPublisher publisher) {
        String title = ReportTemplate.DEFAULT.title();
        String summary = ReportPrompts.REPORT_SYSTEM_PROMPT + " " + request.query();
        List<ChapterSpec> chapterSpecs = buildChapterSpecs(input);
        List<DocumentBlock> blocks = new ArrayList<>();

        for (int index = 0; index < chapterSpecs.size(); index++) {
            ChapterSpec chapterSpec = chapterSpecs.get(index);
            ChapterGenerationResult chapter = chapterGenerationNode.generate(request.query(), chapterSpec);
            blocks.addAll(chapter.blocks());
            publisher.publish(new DeltaChunkEvent(
                request.taskId(),
                "REPORT",
                "chapter-" + chapterSpec.chapterId(),
                summarizeChapter(chapter),
                index + 1,
                Instant.now()
            ));
        }

        DocumentIr documentIr = new DocumentIr(
            new DocumentMeta(title, summary, request.query(), "default", Instant.now()),
            blocks
        );
        return new ReportDocument(title, summary, documentIr, htmlRenderer.render(documentIr));
    }

    private List<ChapterSpec> buildChapterSpecs(ReportInput input) {
        List<ChapterSpec> chapterSpecs = new ArrayList<>();
        for (EngineResult result : input.engineResults()) {
            chapterSpecs.add(new ChapterSpec(
                result.engineType().name().toLowerCase(),
                result.engineType().name() + " analysis",
                "整合 " + result.engineType().name() + " 引擎结论",
                buildEngineSourceMaterial(result)
            ));
        }
        chapterSpecs.add(new ChapterSpec(
            "forum",
            "Forum summary",
            "整合论坛主持结论与共识",
            "Overview: " + input.forumSummary().overview()
                + "\nConsensus: " + String.join("; ", input.forumSummary().consensusPoints())
                + "\nOpen questions: " + String.join("; ", input.forumSummary().openQuestions())
        ));
        return chapterSpecs;
    }

    private String buildEngineSourceMaterial(EngineResult result) {
        return "Headline: " + result.headline()
            + "\nSummary: " + result.summary()
            + "\nKey points: " + String.join("; ", result.keyPoints());
    }

    private String summarizeChapter(ChapterGenerationResult chapter) {
        String status = chapter.placeholder() ? "placeholder" : "generated";
        return chapter.chapterSpec().title() + " | " + status + " | attempts=" + chapter.attemptsUsed();
    }
}
