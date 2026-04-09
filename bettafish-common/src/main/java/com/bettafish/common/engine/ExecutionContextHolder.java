package com.bettafish.common.engine;

import java.util.function.Supplier;

public final class ExecutionContextHolder {

    private static final ThreadLocal<ExecutionContext> CURRENT = new ThreadLocal<>();

    private ExecutionContextHolder() {
    }

    public static ExecutionContext current() {
        return CURRENT.get();
    }

    public static void runWith(ExecutionContext executionContext, Runnable runnable) {
        callWith(executionContext, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T callWith(ExecutionContext executionContext, Supplier<T> supplier) {
        ExecutionContext previous = CURRENT.get();
        if (executionContext == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(executionContext);
        }
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
