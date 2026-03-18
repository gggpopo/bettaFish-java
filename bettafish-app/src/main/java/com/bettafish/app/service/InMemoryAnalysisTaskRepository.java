package com.bettafish.app.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import com.bettafish.common.api.AnalysisTaskSnapshot;

@Repository
public class InMemoryAnalysisTaskRepository implements AnalysisTaskRepository {

    private final Map<String, AnalysisTaskSnapshot> tasks = new ConcurrentHashMap<>();

    @Override
    public AnalysisTaskSnapshot save(AnalysisTaskSnapshot snapshot) {
        tasks.put(snapshot.taskId(), snapshot);
        return snapshot;
    }

    @Override
    public Optional<AnalysisTaskSnapshot> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }
}
