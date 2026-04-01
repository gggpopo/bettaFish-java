package com.bettafish.common.runtime;

public interface Node<C extends NodeContext> {

    default String name() {
        return getClass().getSimpleName();
    }

    Node<C> execute(C context);
}
