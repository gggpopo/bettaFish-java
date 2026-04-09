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
    private final ExecutionContext parent;
    private final AtomicReference<TerminationReason> terminationReason = new AtomicReference<>();

    public ExecutionContext(Duration timeout) {
        this(timeout, null);
    }

    public ExecutionContext(Duration timeout, ExecutionContext parent) {
        this.createdAt = Instant.now();
        this.deadline = timeout == null ? null : createdAt.plus(timeout);
        this.parent = parent;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deadline() {
        return deadline;
    }

    public Duration remainingTime() {
        promoteDeadlineExpiryToTimeout();
        Duration localRemaining = deadline == null ? null : nonNegative(Duration.between(Instant.now(), deadline));
        if (parent == null) {
            return localRemaining;
        }
        Duration parentRemaining = parent.remainingTime();
        if (localRemaining == null) {
            return parentRemaining;
        }
        if (parentRemaining == null) {
            return localRemaining;
        }
        return localRemaining.compareTo(parentRemaining) <= 0 ? localRemaining : parentRemaining;
    }

    public boolean cancel() {
        return terminationReason.compareAndSet(null, TerminationReason.USER_CANCELLED);
    }

    public boolean timeout() {
        return terminationReason.compareAndSet(null, TerminationReason.TIMED_OUT);
    }

    public boolean isCancellationRequested() {
        return effectiveTerminationReason() != null;
    }

    public boolean isTimedOut() {
        return effectiveTerminationReason() == TerminationReason.TIMED_OUT;
    }

    public boolean isCancelledByUser() {
        return effectiveTerminationReason() == TerminationReason.USER_CANCELLED;
    }

    public TerminationReason terminationReason() {
        return effectiveTerminationReason();
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
        TerminationReason reason = effectiveTerminationReason();
        if (reason != null) {
            throw new ExecutionCancelledException(reason);
        }
    }

    private void promoteDeadlineExpiryToTimeout() {
        if (deadline != null && terminationReason.get() == null && !Instant.now().isBefore(deadline)) {
            timeout();
        }
    }

    private TerminationReason effectiveTerminationReason() {
        promoteDeadlineExpiryToTimeout();
        TerminationReason localReason = terminationReason.get();
        if (localReason != null) {
            return localReason;
        }
        return parent == null ? null : parent.terminationReason();
    }

    private Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
