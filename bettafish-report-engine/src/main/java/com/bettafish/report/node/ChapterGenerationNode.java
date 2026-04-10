package com.bettafish.report.node;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.llm.LlmGateway;
import org.springframework.beans.factory.annotation.Autowired;
import com.bettafish.report.ir.ChapterGenerationResult;
import com.bettafish.report.ir.ChapterSpec;
import com.bettafish.report.prompt.ReportPrompts;

@Component
public class ChapterGenerationNode {

    static final String REPORT_CLIENT = "reportChatClient";
    static final int DEFAULT_MAX_ATTEMPTS = 2;
    static final int DEFAULT_MIN_DENSITY = 40;

    private final LlmGateway llmGateway;
    private final int maxAttempts;
    private final int minDensity;

    @Autowired
    public ChapterGenerationNode(LlmGateway llmGateway) {
        this(llmGateway, DEFAULT_MAX_ATTEMPTS, DEFAULT_MIN_DENSITY);
    }

    ChapterGenerationNode(LlmGateway llmGateway, int maxAttempts, int minDensity) {
        this.llmGateway = llmGateway;
        this.maxAttempts = maxAttempts;
        this.minDensity = minDensity;
    }

    public ChapterGenerationResult generate(String query, ChapterSpec chapterSpec) {
        String lastFailure = "unknown";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ChapterDraftResponse response = llmGateway.callJson(
                REPORT_CLIENT,
                ReportPrompts.CHAPTER_GENERATION_SYSTEM_PROMPT,
                ReportPrompts.buildChapterGenerationUserPrompt(query, chapterSpec, attempt, attempt == 1 ? null : lastFailure),
                ChapterDraftResponse.class,
                this::validateDraftResponse,
                () -> new ChapterDraftResponse(List.of())
            );

            List<DocumentBlock> normalizedBlocks = normalizeBlocks(chapterSpec, response.blocks());
            String validationFailure = validateBlocks(normalizedBlocks);
            if (validationFailure == null) {
                return new ChapterGenerationResult(chapterSpec, normalizedBlocks, false, attempt);
            }
            lastFailure = validationFailure;
        }

        return new ChapterGenerationResult(chapterSpec, placeholderBlocks(chapterSpec, lastFailure), true, maxAttempts);
    }

    private List<DocumentBlock> normalizeBlocks(ChapterSpec chapterSpec, List<DocumentBlock> blocks) {
        List<DocumentBlock> normalized = new ArrayList<>();
        normalized.add(new DocumentBlock.HeadingBlock(2, chapterSpec.title()));
        for (DocumentBlock block : blocks) {
            if (block instanceof DocumentBlock.HeadingBlock headingBlock && headingBlock.level() <= 2) {
                continue;
            }
            normalized.add(block);
        }
        return List.copyOf(normalized);
    }

    private String validateBlocks(List<DocumentBlock> blocks) {
        if (blocks.size() <= 1) {
            return "chapter contains no body blocks";
        }

        int density = 0;
        for (int index = 1; index < blocks.size(); index++) {
            DocumentBlock block = blocks.get(index);
            String error = validateBlock(block);
            if (error != null) {
                return error;
            }
            density += visibleLength(block);
        }

        if (density < minDensity) {
            return "chapter density below threshold: " + density;
        }
        return null;
    }

    private String validateBlock(DocumentBlock block) {
        return switch (block) {
            case DocumentBlock.ParagraphBlock paragraphBlock ->
                isBlank(paragraphBlock.text()) ? "paragraph text is blank" : null;
            case DocumentBlock.ListBlock listBlock ->
                listBlock.items().isEmpty() ? "list items are empty" : null;
            case DocumentBlock.QuoteBlock quoteBlock ->
                isBlank(quoteBlock.text()) ? "quote text is blank" : null;
            case DocumentBlock.TableBlock tableBlock ->
                tableBlock.headers().isEmpty() ? "table headers are empty" : null;
            case DocumentBlock.CodeBlock codeBlock ->
                isBlank(codeBlock.code()) ? "code block is blank" : null;
            case DocumentBlock.LinkBlock linkBlock ->
                isBlank(linkBlock.text()) || isBlank(linkBlock.href()) ? "link is incomplete" : null;
            case DocumentBlock.HeadingBlock ignored -> null;
            case DocumentBlock.WidgetBlock widgetBlock ->
                widgetBlock.config() == null || widgetBlock.config().isEmpty() ? "widget config is empty" : null;
            case DocumentBlock.CalloutBlock calloutBlock ->
                isBlank(calloutBlock.text()) ? "callout text is blank" : null;
            case DocumentBlock.KpiGridBlock kpiGridBlock ->
                kpiGridBlock.items().isEmpty() ? "kpi grid items are empty" : null;
            case DocumentBlock.HrBlock ignored2 -> null;
            case DocumentBlock.SwotTableBlock swotBlock ->
                isBlank(swotBlock.title()) ? "swot table title is blank" : null;
            case DocumentBlock.PestTableBlock pestBlock ->
                isBlank(pestBlock.title()) ? "pest table title is blank" : null;
            case DocumentBlock.EngineQuoteBlock engineQuoteBlock ->
                engineQuoteBlock.blocks().isEmpty() ? "engine quote blocks are empty" : null;
            case DocumentBlock.MathBlock mathBlock ->
                isBlank(mathBlock.latex()) ? "math latex is blank" : null;
        };
    }

    private int visibleLength(DocumentBlock block) {
        return switch (block) {
            case DocumentBlock.ParagraphBlock paragraphBlock -> paragraphBlock.text().trim().length();
            case DocumentBlock.ListBlock listBlock -> listBlock.items().stream().mapToInt(String::length).sum();
            case DocumentBlock.QuoteBlock quoteBlock -> quoteBlock.text().trim().length();
            case DocumentBlock.TableBlock tableBlock ->
                tableBlock.headers().stream().mapToInt(String::length).sum()
                    + tableBlock.rows().stream().flatMap(List::stream).mapToInt(String::length).sum();
            case DocumentBlock.CodeBlock codeBlock -> codeBlock.code().trim().length();
            case DocumentBlock.LinkBlock linkBlock -> linkBlock.text().trim().length() + linkBlock.href().trim().length();
            case DocumentBlock.HeadingBlock ignored -> 0;
            case DocumentBlock.WidgetBlock widgetBlock -> widgetBlock.chartType().length() + 10;
            case DocumentBlock.CalloutBlock calloutBlock -> calloutBlock.text().length();
            case DocumentBlock.KpiGridBlock kpiGridBlock ->
                kpiGridBlock.items().stream().mapToInt(item -> item.label().length() + item.value().length()).sum();
            case DocumentBlock.HrBlock ignored2 -> 3;
            case DocumentBlock.SwotTableBlock swotBlock ->
                swotBlock.title().length()
                    + swotBlock.strengths().stream().mapToInt(String::length).sum()
                    + swotBlock.weaknesses().stream().mapToInt(String::length).sum()
                    + swotBlock.opportunities().stream().mapToInt(String::length).sum()
                    + swotBlock.threats().stream().mapToInt(String::length).sum();
            case DocumentBlock.PestTableBlock pestBlock ->
                pestBlock.title().length()
                    + pestBlock.political().stream().mapToInt(String::length).sum()
                    + pestBlock.economic().stream().mapToInt(String::length).sum()
                    + pestBlock.social().stream().mapToInt(String::length).sum()
                    + pestBlock.technological().stream().mapToInt(String::length).sum();
            case DocumentBlock.EngineQuoteBlock engineQuoteBlock ->
                engineQuoteBlock.title().length()
                    + engineQuoteBlock.blocks().stream().mapToInt(this::visibleLength).sum();
            case DocumentBlock.MathBlock mathBlock -> mathBlock.latex().length();
        };
    }

    private List<DocumentBlock> placeholderBlocks(ChapterSpec chapterSpec, String failureReason) {
        return List.of(
            new DocumentBlock.HeadingBlock(2, chapterSpec.title()),
            new DocumentBlock.ParagraphBlock("本章节生成失败，已写入占位内容。原因：" + failureReason + "。")
        );
    }

    private LlmGateway.ValidationResult validateDraftResponse(ChapterDraftResponse response) {
        if (response == null) {
            return LlmGateway.ValidationResult.invalid("chapter draft response must not be null");
        }
        if (response.blocks() == null) {
            return LlmGateway.ValidationResult.invalid("chapter draft blocks must not be null");
        }
        boolean hasNullBlock = response.blocks().stream().anyMatch(block -> block == null);
        if (hasNullBlock) {
            return LlmGateway.ValidationResult.invalid("chapter draft blocks must not contain null entries");
        }
        return LlmGateway.ValidationResult.valid();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ChapterDraftResponse(List<DocumentBlock> blocks) {
        public ChapterDraftResponse {
            blocks = List.copyOf(blocks);
        }
    }
}
