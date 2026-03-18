package com.bettafish.common.model;

public record SearchDecision(
    boolean shouldSearch,
    String query,
    String rationale
) {
}
