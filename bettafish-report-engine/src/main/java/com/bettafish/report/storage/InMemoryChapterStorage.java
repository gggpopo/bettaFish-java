package com.bettafish.report.storage;

import com.bettafish.report.ir.ChapterGenerationResult;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryChapterStorage implements ChapterStorage {

    private final Map<String, Map<String, ChapterGenerationResult>> store = new ConcurrentHashMap<>();

    @Override
    public void save(String taskId, String chapterId, ChapterGenerationResult result) {
        store.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).put(chapterId, result);
    }

    @Override
    public Optional<ChapterGenerationResult> load(String taskId, String chapterId) {
        return Optional.ofNullable(store.getOrDefault(taskId, Map.of()).get(chapterId));
    }

    @Override
    public List<ChapterGenerationResult> loadAll(String taskId) {
        return List.copyOf(store.getOrDefault(taskId, Map.of()).values());
    }
}
