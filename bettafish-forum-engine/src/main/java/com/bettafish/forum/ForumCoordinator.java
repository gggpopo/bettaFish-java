package com.bettafish.forum;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ForumSummary;

@Service
public class ForumCoordinator implements com.bettafish.common.engine.ForumCoordinator {

    private final ForumHost forumHost;

    public ForumCoordinator(ForumHost forumHost) {
        this.forumHost = forumHost;
    }

    @Override
    public ForumSummary coordinate(AnalysisRequest request, List<EngineResult> engineResults) {
        List<String> consensusPoints = engineResults.stream()
            .map(result -> result.engineType().name() + ": " + result.headline())
            .collect(Collectors.toList());
        List<String> openQuestions = List.of(
            "Which evidence requires live retrieval next?",
            "Which audience segment should be tracked more closely?"
        );
        String overview = forumHost.moderate(request.query(), engineResults.size());
        return new ForumSummary(overview, consensusPoints, openQuestions);
    }
}
