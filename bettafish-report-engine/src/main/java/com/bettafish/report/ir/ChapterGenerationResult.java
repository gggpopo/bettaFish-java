package com.bettafish.report.ir;

import java.util.List;
import com.bettafish.common.api.DocumentBlock;

public record ChapterGenerationResult(
    ChapterSpec chapterSpec,
    List<DocumentBlock> blocks,
    boolean placeholder,
    int attemptsUsed
) {
    public ChapterGenerationResult {
        blocks = List.copyOf(blocks);
    }
}
