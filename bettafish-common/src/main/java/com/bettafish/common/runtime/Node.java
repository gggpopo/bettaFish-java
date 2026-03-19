package com.bettafish.common.runtime;

public interface Node<N extends Enum<N> & Node<N, C>, C extends NodeContext<N>> {

    N execute(C context);
}
