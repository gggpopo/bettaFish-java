package com.bettafish.common.runtime;

public class StateMachineRunner<N extends Enum<N> & Node<N, C>, C extends NodeContext<N>> {

    private static final int DEFAULT_MAX_STEPS = 10_000;

    private final int maxSteps;

    public StateMachineRunner() {
        this(DEFAULT_MAX_STEPS);
    }

    public StateMachineRunner(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public C run(C context, N startNode) {
        N node = startNode;
        int steps = 0;
        while (node != null) {
            steps++;
            if (steps > maxSteps) {
                throw new IllegalStateException("State machine exceeded max steps: " + maxSteps);
            }
            context.enterNode(node);
            node = node.execute(context);
        }
        return context;
    }
}
