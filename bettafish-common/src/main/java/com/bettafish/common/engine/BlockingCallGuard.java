package com.bettafish.common.engine;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class BlockingCallGuard {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private static final ExecutorService BLOCKING_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private BlockingCallGuard() {
    }

    public static <T> T call(String operationName, CheckedSupplier<T> supplier) {
        return call(operationName, ExecutionContextHolder.current(), supplier);
    }

    public static <T> T call(String operationName, ExecutionContext executionContext, CheckedSupplier<T> supplier) {
        if (executionContext == null) {
            return invoke(operationName, supplier);
        }

        Future<T> future = BLOCKING_EXECUTOR.submit(() -> invoke(operationName, supplier));
        try {
            while (true) {
                throwIfCancelled(executionContext, future);
                try {
                    return future.get(waitSliceMillis(executionContext), TimeUnit.MILLISECONDS);
                } catch (TimeoutException ex) {
                    throwIfCancelled(executionContext, future);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            if (executionContext.isCancellationRequested()) {
                executionContext.throwIfCancellationRequested();
            }
            throw new IllegalStateException("Interrupted while waiting for " + operationName, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ExecutionCancelledException cancelled) {
                throw cancelled;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Blocking call failed for " + operationName, cause);
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {

        T get() throws Exception;
    }

    private static <T> T invoke(String operationName, CheckedSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ExecutionCancelledException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Blocking call failed for " + operationName, ex);
        }
    }

    private static void throwIfCancelled(ExecutionContext executionContext, Future<?> future) {
        try {
            executionContext.throwIfCancellationRequested();
        } catch (ExecutionCancelledException ex) {
            future.cancel(true);
            throw ex;
        }
    }

    private static long waitSliceMillis(ExecutionContext executionContext) {
        Duration remaining = executionContext.remainingTime();
        if (remaining == null) {
            return POLL_INTERVAL.toMillis();
        }
        if (remaining.isZero()) {
            executionContext.throwIfCancellationRequested();
        }
        return Math.max(1L, Math.min(POLL_INTERVAL.toMillis(), remaining.toMillis()));
    }
}
