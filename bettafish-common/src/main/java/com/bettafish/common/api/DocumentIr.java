package com.bettafish.common.api;

import java.util.ArrayList;
import java.util.List;

public record DocumentIr(
    DocumentMeta meta,
    List<DocumentBlock> blocks
) {
    public DocumentIr {
        blocks = List.copyOf(blocks);
    }

    public List<ReportSection> toSections() {
        List<ReportSection> sections = new ArrayList<>();
        String currentTitle = null;
        List<String> currentContent = new ArrayList<>();

        for (DocumentBlock block : blocks) {
            if (block instanceof DocumentBlock.HeadingBlock headingBlock) {
                if (headingBlock.level() <= 2) {
                    if (currentTitle != null) {
                        sections.add(new ReportSection(currentTitle, joinContent(currentContent)));
                    }
                    currentTitle = headingBlock.text();
                    currentContent = new ArrayList<>();
                    continue;
                }
            }

            if (currentTitle == null) {
                currentTitle = meta.title();
            }
            appendBlock(currentContent, block);
        }

        if (currentTitle != null) {
            sections.add(new ReportSection(currentTitle, joinContent(currentContent)));
        }
        return List.copyOf(sections);
    }

    private void appendBlock(List<String> currentContent, DocumentBlock block) {
        switch (block) {
            case DocumentBlock.ParagraphBlock paragraphBlock ->
                currentContent.add(paragraphBlock.text());
            case DocumentBlock.ListBlock listBlock ->
                currentContent.add(renderList(listBlock));
            case DocumentBlock.QuoteBlock quoteBlock ->
                currentContent.add(renderQuote(quoteBlock));
            case DocumentBlock.TableBlock tableBlock ->
                currentContent.add(renderTable(tableBlock));
            case DocumentBlock.CodeBlock codeBlock ->
                currentContent.add(renderCode(codeBlock));
            case DocumentBlock.LinkBlock linkBlock ->
                currentContent.add(linkBlock.text() + " (" + linkBlock.href() + ")");
            case DocumentBlock.HeadingBlock headingBlock ->
                currentContent.add("#".repeat(Math.max(1, headingBlock.level())) + " " + headingBlock.text());
        }
    }

    private String renderList(DocumentBlock.ListBlock listBlock) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < listBlock.items().size(); index++) {
            String prefix = listBlock.ordered() ? (index + 1) + ". " : "- ";
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(prefix).append(listBlock.items().get(index));
        }
        return builder.toString();
    }

    private String renderQuote(DocumentBlock.QuoteBlock quoteBlock) {
        if (quoteBlock.attribution() == null || quoteBlock.attribution().isBlank()) {
            return "> " + quoteBlock.text();
        }
        return "> " + quoteBlock.text() + "\n> -- " + quoteBlock.attribution();
    }

    private String renderTable(DocumentBlock.TableBlock tableBlock) {
        List<String> lines = new ArrayList<>();
        lines.add("| " + String.join(" | ", tableBlock.headers()) + " |");
        lines.add("| " + tableBlock.headers().stream().map(ignored -> "---").reduce((l, r) -> l + " | " + r).orElse("---") + " |");
        for (List<String> row : tableBlock.rows()) {
            lines.add("| " + String.join(" | ", row) + " |");
        }
        return String.join("\n", lines);
    }

    private String renderCode(DocumentBlock.CodeBlock codeBlock) {
        return "```" + (codeBlock.language() == null ? "" : codeBlock.language()) + "\n"
            + codeBlock.code()
            + "\n```";
    }

    private String joinContent(List<String> content) {
        return content.stream()
            .filter(value -> value != null && !value.isBlank())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElse("");
    }
}
