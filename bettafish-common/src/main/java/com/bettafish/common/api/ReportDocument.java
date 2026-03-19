package com.bettafish.common.api;

import java.util.List;

public record ReportDocument(
    String title,
    String summary,
    DocumentIr documentIr,
    String html
) {

    public ReportDocument(String title, String summary, List<ReportSection> sections, String html) {
        this(title, summary, legacyDocumentIr(title, summary, sections), html);
    }

    public List<ReportSection> sections() {
        return documentIr.toSections();
    }

    private static DocumentIr legacyDocumentIr(String title, String summary, List<ReportSection> sections) {
        List<DocumentBlock> blocks = new java.util.ArrayList<>();
        for (ReportSection section : sections) {
            blocks.add(new DocumentBlock.HeadingBlock(2, section.title()));
            blocks.add(new DocumentBlock.ParagraphBlock(section.content()));
        }
        return new DocumentIr(
            new DocumentMeta(title, summary, "", "legacy", java.time.Instant.EPOCH),
            blocks
        );
    }
}
