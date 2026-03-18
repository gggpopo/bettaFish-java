package com.bettafish.common.engine;

import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.ReportDocument;
import com.bettafish.common.api.ReportInput;

@FunctionalInterface
public interface ReportGenerator {

    ReportDocument generate(AnalysisRequest request, ReportInput input);
}
