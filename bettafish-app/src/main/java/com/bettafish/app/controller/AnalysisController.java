package com.bettafish.app.controller;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.bettafish.app.service.AnalysisCoordinator;
import com.bettafish.common.api.AnalysisTaskSnapshot;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisCoordinator analysisCoordinator;

    public AnalysisController(AnalysisCoordinator analysisCoordinator) {
        this.analysisCoordinator = analysisCoordinator;
    }

    @PostMapping
    public ResponseEntity<AnalysisTaskSnapshot> createAnalysis(@RequestBody CreateAnalysisTaskRequest request) {
        return ResponseEntity.ok(analysisCoordinator.startAnalysis(request.query()));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<AnalysisTaskSnapshot> getAnalysis(@PathVariable String taskId) {
        return analysisCoordinator.getTask(taskId)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found: " + taskId));
    }
}
