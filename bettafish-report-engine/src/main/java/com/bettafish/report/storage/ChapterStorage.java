package com.bettafish.report.storage;

import com.bettafish.report.ir.ChapterGenerationResult;
import java.util.List;
import java.util.Optional;

public interface ChapterStorage {
    void save(String taskId, String chapterId, ChapterGenerationResult result);
    Optional<ChapterGenerationResult> load(String taskId, String chapterId);
    List<ChapterGenerationResult> loadAll(String taskId);
}
