package com.bettafish.common.model;

public record ParagraphState(
    String paragraphId,
    String title,
    String summary,
    boolean complete
) {
}
