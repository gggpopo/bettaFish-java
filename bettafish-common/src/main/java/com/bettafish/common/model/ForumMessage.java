package com.bettafish.common.model;

import java.time.Instant;

public record ForumMessage(
    String speaker,
    String role,
    String content,
    Instant createdAt
) {
}
