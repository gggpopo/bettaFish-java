package com.bettafish.common.api;

import java.util.List;

public record ForumSummary(
    String overview,
    List<String> consensusPoints,
    List<String> openQuestions
) {
}
