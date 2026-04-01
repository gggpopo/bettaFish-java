package com.bettafish.common.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import com.bettafish.common.api.AnalysisStatus;

public class ExecutionContext {

    public enum TerminationReason {
        USER_CANCELLED,
        TIMED_OUT
    }

    private final Instant createdAt;
    private final Instant deadline;
    private final AtomicReference<TerminationReason> terminationReason = new AtomicReference<>();

    public ExecutionContext(Duration timeout) {
        this.createdAt = Instant.now();
        this.deadline = timeout == null ? null : createdAt.plus(timeout);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deadline() {
        return deadline;
    }

    public boolean cancel() {
        return terminationReason.compareAndSet(null, TerminationReason.USER_CANCELLED);
    }

    public boolean timeout() {
        return terminationReason.compareAndSet(null, TerminationReason.TIMED_OUT);
    }

    public boolean isCancellationRequested() {
        return terminationReason.get() != null;
    }

    public boolean isTimedOut() {
        return terminationReason.get() == TerminationReason.TIMED_OUT;
    }

    public boolean isCancelledByUser() {
        return terminationReason.get() == TerminationReason.USER_CANCELLED;
    }

    public TerminationReason terminationReason() {
        return terminationReason.get();
    }

    public AnalysisStatus terminalStatus() {
        TerminationReason reason = terminationReason.get();
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case USER_CANCELLED -> AnalysisStatus.CANCELLED;
            case TIMED_OUT -> AnalysisStatus.TIMED_OUT;
        };
    }

    public String terminalMessage() {
        TerminationReason reason = terminationReason.get();
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case USER_CANCELLED -> "Analysis task was cancelled";
            case TIMED_OUT -> "Analysis task timed out";
        };
    }

    public void throwIfCancellationRequested() {
        TerminationReason reason = terminationReason.get();
        if (reason != null) {
            throw new ExecutionCancelledException(reason);
        }
    }
}
