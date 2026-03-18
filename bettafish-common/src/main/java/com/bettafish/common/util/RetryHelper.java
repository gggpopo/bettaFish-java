package com.bettafish.common.util;

import java.time.Duration;
import java.util.function.Supplier;

public final class RetryHelper {

    private RetryHelper() {
    }

    public static <T> T withRetry(int maxAttempts, Duration delay, Supplier<T> supplier) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
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
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry sleep interrupted", ex);
        }
    }
}
