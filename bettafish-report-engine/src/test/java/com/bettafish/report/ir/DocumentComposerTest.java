package com.bettafish.report.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.api.DocumentBlock;
import com.bettafish.common.api.DocumentIr;

class DocumentComposerTest {

    private final DocumentComposer composer = new DocumentComposer();

    private ChapterGenerationResult chapter(String id, DocumentBlock... blocks) {
        return new ChapterGenerationResult(
            new ChapterSpec(id, "Chapter " + id, "objective", "source"),
            List.of(blocks),
            false,
            1
        );
    }

    @Test
    void compose_withMultipleChapters_insertsHrBetween() {
        var ch1 = chapter("c1", new DocumentBlock.ParagraphBlock("Para 1"));
        var ch2 = chapter("c2", new DocumentBlock.ParagraphBlock("Para 2"));

        DocumentIr ir = composer.compose("Title", "Summary", "query", "default", List.of(ch1, ch2));

        List<DocumentBlock> blocks = ir.blocks();
        // blocks[0] = HeadingBlock(title), blocks[1] = Para1, blocks[2] = HrBlock, blocks[3] = Para2
        assertEquals(4, blocks.size());
        assertInstanceOf(DocumentBlock.HeadingBlock.class, blocks.get(0));
        assertInstanceOf(DocumentBlock.ParagraphBlock.class, blocks.get(1));
        assertInstanceOf(DocumentBlock.HrBlock.class, blocks.get(2));
        assertInstanceOf(DocumentBlock.ParagraphBlock.class, blocks.get(3));
    }

    @Test
    void compose_withEmptyChapters_returnsOnlyTitle() {
        DocumentIr ir = composer.compose("Title", "Summary", "query", "default", List.of());

        assertEquals(1, ir.blocks().size());
        assertInstanceOf(DocumentBlock.HeadingBlock.class, ir.blocks().get(0));
        assertEquals("Title", ((DocumentBlock.HeadingBlock) ir.blocks().get(0)).text());
    }

    @Test
    void compose_setsMetaFields() {
        DocumentIr ir = composer.compose("My Title", "My Summary", "my query", "tmpl", List.of());

        assertEquals("My Title", ir.meta().title());
        assertEquals("My Summary", ir.meta().summary());
        assertEquals("my query", ir.meta().query());
        assertEquals("tmpl", ir.meta().template());
        assertNotNull(ir.meta().generatedAt());
    }
}
