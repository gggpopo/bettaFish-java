package com.bettafish.common.api;

import java.time.Instant;

public record DocumentMeta(
    String title,
    String summary,
    String query,
    String template,
    Instant generatedAt
) {
}
