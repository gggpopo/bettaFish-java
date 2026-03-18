package com.bettafish.app.service;

import java.util.Optional;
import com.bettafish.common.api.AnalysisTaskSnapshot;

public interface AnalysisTaskRepository {

    AnalysisTaskSnapshot save(AnalysisTaskSnapshot snapshot);

    Optional<AnalysisTaskSnapshot> findById(String taskId);
}
