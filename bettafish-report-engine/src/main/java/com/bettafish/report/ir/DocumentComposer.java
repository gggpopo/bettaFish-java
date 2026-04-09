package com.bettafish.report.ir;

import com.bettafish.common.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentComposer {

    private static final Logger log = LoggerFactory.getLogger(DocumentComposer.class);

    public DocumentIr compose(String title, String summary, String query, String template,
                              List<ChapterGenerationResult> chapters) {
        List<DocumentBlock> allBlocks = new ArrayList<>();

        // Title heading
        allBlocks.add(new DocumentBlock.HeadingBlock(1, title));

        for (int i = 0; i < chapters.size(); i++) {
            if (i > 0) {
                allBlocks.add(new DocumentBlock.HrBlock());
            }
            allBlocks.addAll(chapters.get(i).blocks());
        }

        DocumentMeta meta = new DocumentMeta(title, summary, query, template, Instant.now());
        DocumentIr ir = new DocumentIr(meta, allBlocks);

        // Validate and log warnings
        var errors = IrValidator.validate(ir);
        if (!errors.isEmpty()) {
            log.warn("DocumentComposer: IR validation found {} issues", errors.size());
            errors.forEach(e -> log.warn("  {} : {}", e.path(), e.message()));
        }

        return ir;
    }
}
