package com.bettafish.common.engine;

import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;
import com.bettafish.common.event.AnalysisEventPublisher;

@FunctionalInterface
public interface ReportGenerator {

    ReportDocument generate(AnalysisRequest request, ReportInput input);

    default ReportDocument generate(AnalysisRequest request, ReportInput input, AnalysisEventPublisher publisher) {
        return generate(request, input);
    }
}
