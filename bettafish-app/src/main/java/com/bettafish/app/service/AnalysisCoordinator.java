package com.bettafish.app.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;
import com.bettafish.app.event.EventBus;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.AnalysisStatus;
import com.bettafish.common.api.AnalysisTaskSnapshot;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.common.engine.ForumCoordinator;
import com.bettafish.common.engine.ReportGenerator;
import com.bettafish.common.event.AnalysisCompleteEvent;
import com.bettafish.common.event.AnalysisFailedEvent;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.DeltaChunkEvent;
import com.bettafish.common.event.EngineStartedEvent;

@Service
public class AnalysisCoordinator {

    private final List<AnalysisEngine> analysisEngines;
    private final ForumCoordinator forumCoordinator;
    private final ReportGenerator reportGenerator;
    private final AnalysisTaskRepository taskRepository;
    private final Executor analysisExecutor;
    private final EventBus eventBus;

    public AnalysisCoordinator(
        List<AnalysisEngine> analysisEngines,
        ForumCoordinator forumCoordinator,
        ReportGenerator reportGenerator,
        AnalysisTaskRepository taskRepository,
        Executor analysisExecutor,
        EventBus eventBus
    ) {
        this.analysisEngines = analysisEngines;
        this.forumCoordinator = forumCoordinator;
        this.reportGenerator = reportGenerator;
        this.taskRepository = taskRepository;
        this.analysisExecutor = analysisExecutor;
        this.eventBus = eventBus;
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
                .map(engine -> CompletableFuture.supplyAsync(() -> analyzeEngine(request, engine), analysisExecutor))
                .map(CompletableFuture::join)
                .sorted(Comparator.comparing(EngineResult::engineType))
                .toList();
            var forumSummary = forumCoordinator.coordinate(request, engineResults, eventBus);
            ReportDocument report = reportGenerator.generate(
                request,
                new ReportInput(query, engineResults, forumSummary),
                eventBus
            );
            eventBus.publish(new DeltaChunkEvent(taskId, "REPORT", "report-summary", report.summary(), 1, Instant.now()));
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
            AnalysisTaskSnapshot saved = taskRepository.save(completed);
            eventBus.publish(new AnalysisCompleteEvent(taskId, saved, Instant.now()));
            return saved;
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
            AnalysisTaskSnapshot saved = taskRepository.save(failed);
            eventBus.publish(new AnalysisFailedEvent(taskId, saved, Instant.now()));
            return saved;
        }
    }

    public Optional<AnalysisTaskSnapshot> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    private EngineResult analyzeEngine(AnalysisRequest request, AnalysisEngine engine) {
        eventBus.publish(new EngineStartedEvent(request.taskId(), engine.engineName(), Instant.now()));
        EngineResult result = engine.analyze(request, eventBus);
        eventBus.publish(new AgentSpeechEvent(
            request.taskId(),
            engine.engineName(),
            result.headline() + " | " + result.summary(),
            Instant.now()
        ));
        return result;
    }
}
