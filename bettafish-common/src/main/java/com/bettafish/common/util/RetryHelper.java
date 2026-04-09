package com.bettafish.common.util;

import java.time.Duration;
import java.util.function.Supplier;
import com.bettafish.common.engine.ExecutionCancelledException;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ExecutionContextHolder;

public final class RetryHelper {

    private RetryHelper() {
    }

    public static <T> T withRetry(int maxAttempts, Duration delay, Supplier<T> supplier) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            throwIfCancelled();
            try {
                return supplier.get();
            } catch (ExecutionCancelledException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt == maxAttempts) {
                    break;
                }
                sleep(delay);
            }
        }
        throw lastFailure == null ? new IllegalStateException("Retry failed without exception") : lastFailure;
    }

    private static void sleep(Duration delay) {
        long remainingMillis = delay == null ? 0L : Math.max(0L, delay.toMillis());
        while (remainingMillis > 0L) {
            throwIfCancelled();
            long slice = Math.min(remainingMillis, 50L);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ExecutionContext executionContext = ExecutionContextHolder.current();
                if (executionContext != null && executionContext.isCancellationRequested()) {
                    executionContext.throwIfCancellationRequested();
                }
                throw new IllegalStateException("Retry sleep interrupted", ex);
            }
            remainingMillis -= slice;
        }
    }

    private static void throwIfCancelled() {
        ExecutionContext executionContext = ExecutionContextHolder.current();
        if (executionContext != null) {
            executionContext.throwIfCancellationRequested();
        }
    }
}
