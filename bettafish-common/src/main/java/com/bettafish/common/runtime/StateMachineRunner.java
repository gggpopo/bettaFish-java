package com.bettafish.common.runtime;

import com.bettafish.common.engine.ExecutionContext;

public class StateMachineRunner<C extends NodeContext> {

    private static final int DEFAULT_MAX_STEPS = 10_000;

    private final int maxSteps;

    public StateMachineRunner() {
        this(DEFAULT_MAX_STEPS);
    }

    public StateMachineRunner(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public C run(C context, Node<C> startNode) {
        Node<C> node = startNode;
        int steps = 0;
        while (node != null) {
            steps++;
            if (steps > maxSteps) {
                throw new IllegalStateException("State machine exceeded max steps: " + maxSteps);
            }
            ExecutionContext executionContext = context.getService(ExecutionContext.class);
            if (executionContext != null) {
                executionContext.throwIfCancellationRequested();
            }
            context.enterNode(node);
            node = node.execute(context);
        }
        return context;
    }
}
