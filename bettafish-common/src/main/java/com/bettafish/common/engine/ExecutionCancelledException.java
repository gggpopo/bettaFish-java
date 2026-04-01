package com.bettafish.common.engine;

import com.bettafish.common.api.AnalysisStatus;

public class ExecutionCancelledException extends RuntimeException {

    private final ExecutionContext.TerminationReason terminationReason;

    public ExecutionCancelledException(ExecutionContext.TerminationReason terminationReason) {
        super(messageFor(terminationReason));
        this.terminationReason = terminationReason;
    }

    public ExecutionContext.TerminationReason terminationReason() {
        return terminationReason;
    }

    public AnalysisStatus terminalStatus() {
        return switch (terminationReason) {
            case USER_CANCELLED -> AnalysisStatus.CANCELLED;
            case TIMED_OUT -> AnalysisStatus.TIMED_OUT;
        };
    }

    private static String messageFor(ExecutionContext.TerminationReason terminationReason) {
        return switch (terminationReason) {
            case USER_CANCELLED -> "Analysis task was cancelled";
            case TIMED_OUT -> "Analysis task timed out";
        };
    }
}
