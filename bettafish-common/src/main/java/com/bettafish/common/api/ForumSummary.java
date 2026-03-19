package com.bettafish.common.api;

import java.util.List;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;

public record ForumSummary(
    String overview,
    List<String> consensusPoints,
    List<String> openQuestions,
    List<ForumMessage> transcript,
    List<ForumGuidance> guidanceHistory
) {
    public ForumSummary {
        consensusPoints = List.copyOf(consensusPoints);
        openQuestions = List.copyOf(openQuestions);
        transcript = List.copyOf(transcript);
        guidanceHistory = List.copyOf(guidanceHistory);
    }

    public ForumSummary(String overview, List<String> consensusPoints, List<String> openQuestions) {
        this(overview, consensusPoints, openQuestions, List.of(), List.of());
    }
}
