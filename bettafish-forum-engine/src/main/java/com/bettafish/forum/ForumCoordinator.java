package com.bettafish.forum;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.ForumSummary;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.HostCommentEvent;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;

@Service
public class ForumCoordinator implements com.bettafish.common.engine.ForumCoordinator {

    private final ForumHost forumHost;
    private final ForumTranscriptService forumTranscriptService;

    public ForumCoordinator(ForumHost forumHost, ForumTranscriptService forumTranscriptService) {
        this.forumHost = forumHost;
        this.forumTranscriptService = forumTranscriptService;
    }

    @Override
    public ForumSummary coordinate(AnalysisRequest request, List<EngineResult> engineResults) {
        return coordinate(request, engineResults, AnalysisEventPublisher.noop());
    }

    @Override
    public ForumSummary coordinate(AnalysisRequest request, List<EngineResult> engineResults,
                                   AnalysisEventPublisher publisher) {
        List<ForumMessage> transcript = forumTranscriptService.transcript(request.taskId());
        List<ForumGuidance> guidanceHistory = forumTranscriptService.guidanceHistory(request.taskId());
        if (guidanceHistory.isEmpty() && !transcript.isEmpty()) {
            forumTranscriptService.ensureGuidance(request.taskId());
            guidanceHistory = forumTranscriptService.guidanceHistory(request.taskId());
        }

        if (transcript.isEmpty()) {
            Instant now = Instant.now();
            transcript = engineResults.stream()
                .map(result -> new ForumMessage(
                    result.engineType().name(),
                    "agent",
                    result.headline() + " | " + result.summary(),
                    now
                ))
                .toList();
        }

        if (guidanceHistory.isEmpty() && !transcript.isEmpty()) {
            ForumGuidance fallbackGuidance = forumHost.moderate(transcript, 1);
            guidanceHistory = List.of(fallbackGuidance);
            publisher.publish(new HostCommentEvent(request.taskId(), "ForumHost", fallbackGuidance.promptAddendum(), Instant.now()));
        }

        ForumGuidance latestGuidance = guidanceHistory.isEmpty() ? null : guidanceHistory.getLast();
        if (latestGuidance == null) {
            return new ForumSummary(
                "Forum guidance unavailable",
                engineResults.stream().map(result -> result.engineType().name() + ": " + result.headline()).toList(),
                List.of("No transcript captured for this task."),
                transcript,
                guidanceHistory
            );
        }

        return new ForumSummary(
            latestGuidance.summary(),
            latestGuidance.focusPoints(),
            combineOpenQuestions(latestGuidance),
            transcript,
            guidanceHistory
        );
    }

    private List<String> combineOpenQuestions(ForumGuidance guidance) {
        return java.util.stream.Stream.concat(
            guidance.challengeQuestions().stream(),
            guidance.evidenceGaps().stream()
        ).toList();
    }
}
