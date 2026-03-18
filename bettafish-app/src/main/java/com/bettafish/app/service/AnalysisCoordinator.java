package com.bettafish.app.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.AnalysisTaskSnapshot;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;

@Service
public class AnalysisCoordinator {

    private final List<AnalysisEngine> analysisEngines;
    private final ForumCoordinator forumCoordinator;
    private final ReportGenerator reportGenerator;
    private final AnalysisTaskRepository taskRepository;
    private final Executor analysisExecutor;

    public AnalysisCoordinator(
        List<AnalysisEngine> analysisEngines,
        ForumCoordinator forumCoordinator,
        ReportGenerator reportGenerator,
        AnalysisTaskRepository taskRepository,
        Executor analysisExecutor
    ) {
        this.analysisEngines = analysisEngines;
        this.forumCoordinator = forumCoordinator;
        this.reportGenerator = reportGenerator;
        this.taskRepository = taskRepository;
        this.analysisExecutor = analysisExecutor;
    }

    public AnalysisTaskSnapshot startAnalysis(String query) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        AnalysisRequest request = new AnalysisRequest(taskId, query, now);
        taskRepository.save(new AnalysisTaskSnapshot(
            taskId,
            query,
            AnalysisStatus.RUNNING,
            now,
            null,
            List.of(),
            null,
            null,
            null
        ));

        try {
            List<EngineResult> engineResults = analysisEngines.stream()
                .map(engine -> CompletableFuture.supplyAsync(() -> engine.analyze(request), analysisExecutor))
                .map(CompletableFuture::join)
                .sorted(Comparator.comparing(EngineResult::engineType))
                .toList();
            var forumSummary = forumCoordinator.coordinate(request, engineResults);
            ReportDocument report = reportGenerator.generate(
                request,
                new ReportInput(query, engineResults, forumSummary)
            );
            AnalysisTaskSnapshot completed = new AnalysisTaskSnapshot(
                taskId,
                query,
                AnalysisStatus.COMPLETED,
                now,
                Instant.now(),
                engineResults,
                forumSummary,
                report,
                null
            );
            return taskRepository.save(completed);
        } catch (RuntimeException ex) {
            AnalysisTaskSnapshot failed = new AnalysisTaskSnapshot(
                taskId,
                query,
                AnalysisStatus.FAILED,
                now,
                Instant.now(),
                List.of(),
                null,
                null,
                ex.getMessage()
            );
            return taskRepository.save(failed);
        }
    }

    public Optional<AnalysisTaskSnapshot> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }
}
