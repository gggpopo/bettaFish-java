package com.bettafish.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportDocumentTest {

    @Test
    void projectsLegacySectionsFromDocumentIr() {
        DocumentIr documentIr = new DocumentIr(
            new DocumentMeta(
                "BettaFish analysis report",
                "Report summary",
                "武汉大学樱花季舆情热度",
                "default",
                Instant.parse("2026-03-19T00:00:00Z")
            ),
            List.of(
                new DocumentBlock.HeadingBlock(2, "Query"),
                new DocumentBlock.ParagraphBlock("Query section first paragraph."),
                new DocumentBlock.ListBlock(false, List.of("Point A", "Point B")),
                new DocumentBlock.HeadingBlock(2, "Forum"),
                new DocumentBlock.ParagraphBlock("Forum section paragraph.")
            )
        );

        ReportDocument report = new ReportDocument(
            "BettaFish analysis report",
            "Report summary",
            documentIr,
            "<html></html>"
        );

        assertEquals(documentIr, report.documentIr());
        assertEquals(2, report.sections().size());
        assertEquals("Query", report.sections().getFirst().title());
        assertEquals(
            """
                Query section first paragraph.

                - Point A
                - Point B
                """.trim(),
            report.sections().getFirst().content()
        );
        assertEquals("Forum", report.sections().get(1).title());
    }
}
