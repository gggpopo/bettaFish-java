package com.bettafish.common.model;

import java.util.List;

public record ForumGuidance(
    int revision,
    String summary,
    List<String> focusPoints,
    List<String> challengeQuestions,
    List<String> evidenceGaps,
    String promptAddendum
) {
    public ForumGuidance {
        focusPoints = List.copyOf(focusPoints);
        challengeQuestions = List.copyOf(challengeQuestions);
        evidenceGaps = List.copyOf(evidenceGaps);
    }
}
