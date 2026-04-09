package com.bettafish.report;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.bettafish.common.model.StructuredNarrativeMetadata;
import com.bettafish.report.ir.ChapterGenerationResult;
import com.bettafish.report.ir.ChapterSpec;
import com.bettafish.report.ir.DocumentComposer;
import com.bettafish.report.node.ChapterGenerationNode;
import com.bettafish.report.node.DocumentLayoutNode;
import com.bettafish.report.node.TemplateSelectionNode;
import com.bettafish.report.node.WordBudgetNode;
import com.bettafish.report.prompt.ReportPrompts;
import com.bettafish.report.renderer.HtmlRenderer;
import com.bettafish.report.template.ReportTemplate;

@Service
public class ReportAgent implements ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportAgent.class);

    private final ChapterGenerationNode chapterGenerationNode;
    private final HtmlRenderer htmlRenderer;
    private final TemplateSelectionNode templateSelectionNode;
    private final DocumentLayoutNode documentLayoutNode;
    private final WordBudgetNode wordBudgetNode;
    private final DocumentComposer documentComposer;

    @org.springframework.beans.factory.annotation.Autowired
    public ReportAgent(ChapterGenerationNode chapterGenerationNode,
                       HtmlRenderer htmlRenderer,
                       @org.springframework.lang.Nullable TemplateSelectionNode templateSelectionNode,
                       @org.springframework.lang.Nullable DocumentLayoutNode documentLayoutNode,
                       @org.springframework.lang.Nullable WordBudgetNode wordBudgetNode,
                       @org.springframework.lang.Nullable DocumentComposer documentComposer) {
        this.chapterGenerationNode = chapterGenerationNode;
        this.htmlRenderer = htmlRenderer;
        this.templateSelectionNode = templateSelectionNode;
        this.documentLayoutNode = documentLayoutNode;
        this.wordBudgetNode = wordBudgetNode;
        this.documentComposer = documentComposer;
    }

    /** Backward-compatible constructor for tests. */
    ReportAgent(ChapterGenerationNode chapterGenerationNode, HtmlRenderer htmlRenderer) {
        this(chapterGenerationNode, htmlRenderer, null, null, null, null);
    }

    @Override
    public ReportDocument generate(AnalysisRequest request, ReportInput input) {
        return generate(request, input, AnalysisEventPublisher.noop());
    }

    @Override
    public ReportDocument generate(AnalysisRequest request, ReportInput input, AnalysisEventPublisher publisher) {
        String materialSummary = buildMaterialSummary(input);

        // Pipeline: template selection → layout → word budget (skip if nodes not injected)
        String templateName = "default";
        String title = ReportTemplate.DEFAULT.title();
        String summary = ReportPrompts.REPORT_SYSTEM_PROMPT + " " + request.query();

        if (templateSelectionNode != null) {
            var templateResult = templateSelectionNode.select(request.query(), materialSummary);
            templateName = templateResult.templateName();
            log.info("Template selected: {} ({})", templateResult.templateName(), templateResult.selectionReason());
        }

        List<ChapterSpec> chapterSpecs;
        if (documentLayoutNode != null && wordBudgetNode != null) {
            var layout = documentLayoutNode.design(request.query(), templateName, materialSummary);
            title = layout.title() != null ? layout.title() : title;
            summary = layout.subtitle() != null ? layout.subtitle() : summary;

            var budget = wordBudgetNode.plan(request.query(), templateName, layout.tocPlan(), materialSummary);
            log.info("Word budget: {} total, {} chapters", budget.totalWords(), budget.chapters().size());

            chapterSpecs = buildChapterSpecsFromPipeline(input, layout, budget);
        } else {
            chapterSpecs = buildChapterSpecs(input);
        }

        // Generate chapters
        List<ChapterGenerationResult> chapters = new ArrayList<>();
        for (int index = 0; index < chapterSpecs.size(); index++) {
            ChapterSpec chapterSpec = chapterSpecs.get(index);
            ChapterGenerationResult chapter = chapterGenerationNode.generate(request.query(), chapterSpec);
            chapters.add(chapter);
            publisher.publish(new DeltaChunkEvent(
                request.taskId(),
                "REPORT",
                "chapter-" + chapterSpec.chapterId(),
                summarizeChapter(chapter),
                index + 1,
                Instant.now()
            ));
        }

        // Compose document
        DocumentIr documentIr;
        if (documentComposer != null) {
            documentIr = documentComposer.compose(title, summary, request.query(), templateName, chapters);
        } else {
            List<DocumentBlock> blocks = new ArrayList<>();
            for (ChapterGenerationResult chapter : chapters) {
                blocks.addAll(chapter.blocks());
            }
            documentIr = new DocumentIr(
                new DocumentMeta(title, summary, request.query(), templateName, Instant.now()),
                blocks
            );
        }

        return new ReportDocument(title, summary, documentIr, htmlRenderer.render(documentIr));
    }

    private List<ChapterSpec> buildChapterSpecsFromPipeline(ReportInput input,
                                                              DocumentLayoutNode.DocumentLayoutResult layout,
                                                              WordBudgetNode.WordBudgetResult budget) {
        List<ChapterSpec> specs = new ArrayList<>();
        var budgetMap = budget.chapters().stream()
            .collect(Collectors.toMap(WordBudgetNode.ChapterBudget::chapterId, b -> b));

        for (var tocEntry : layout.tocPlan()) {
            String sourceMaterial = buildMaterialSummary(input);
            var chapterBudget = budgetMap.get(tocEntry.chapterId());
            String objective = tocEntry.description() != null ? tocEntry.description() : "分析 " + tocEntry.display();
            if (chapterBudget != null) {
                objective += " (目标字数: " + chapterBudget.targetWords() + ")";
            }
            specs.add(new ChapterSpec(tocEntry.chapterId(), tocEntry.display(), objective, sourceMaterial));
        }
        return specs;
    }

    private String buildMaterialSummary(ReportInput input) {
        StringBuilder sb = new StringBuilder();
        for (EngineResult result : input.engineResults()) {
            sb.append(result.engineType().name()).append(": ").append(result.headline()).append("\n");
        }
        sb.append("Forum: ").append(input.forumSummary().overview());
        return sb.toString();
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
        if (StructuredNarrativeMetadata.FORMAT_V1.equals(result.metadata().get(StructuredNarrativeMetadata.FORMAT))) {
            return "Headline: " + result.headline()
                + "\nDraft summary: " + result.metadata().getOrDefault(StructuredNarrativeMetadata.DRAFT_SUMMARY, result.summary())
                + "\nFinal conclusion: " + result.metadata().getOrDefault(StructuredNarrativeMetadata.FINAL_CONCLUSION, result.summary())
                + "\nKey points: " + String.join("; ", result.keyPoints())
                + "\nEvidence gaps: " + result.metadata().getOrDefault(StructuredNarrativeMetadata.EVIDENCE_GAPS, "none");
        }
        return "Headline: " + result.headline()
            + "\nSummary: " + result.summary()
            + "\nKey points: " + String.join("; ", result.keyPoints());
    }

    private String summarizeChapter(ChapterGenerationResult chapter) {
        String status = chapter.placeholder() ? "placeholder" : "generated";
        return chapter.chapterSpec().title() + " | " + status + " | attempts=" + chapter.attemptsUsed();
    }
}
