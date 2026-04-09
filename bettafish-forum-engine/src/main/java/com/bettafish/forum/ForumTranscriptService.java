package com.bettafish.forum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.bettafish.common.engine.ForumGuidanceProvider;
import com.bettafish.common.event.AgentSpeechEvent;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.AnalysisEventSubscriber;
import com.bettafish.common.event.HostCommentEvent;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;

@Service
public class ForumTranscriptService implements AnalysisEventSubscriber, ForumGuidanceProvider {

    static final int DEFAULT_SPEECHES_PER_GUIDANCE = 2;
    static final int DEFAULT_MAX_ROUNDS = 3;
    private static final double CONVERGENCE_THRESHOLD = 0.6;

    private final ForumHost forumHost;
    private final AnalysisEventPublisher eventPublisher;
    private final int speechesPerGuidance;
    private final int maxRounds;
    private final Map<String, TaskForumState> taskStates = new ConcurrentHashMap<>();

    @Autowired
    public ForumTranscriptService(ForumHost forumHost, @Lazy AnalysisEventPublisher eventPublisher) {
        this(forumHost, eventPublisher, DEFAULT_SPEECHES_PER_GUIDANCE, DEFAULT_MAX_ROUNDS);
    }

    ForumTranscriptService(ForumHost forumHost, AnalysisEventPublisher eventPublisher, int speechesPerGuidance) {
        this(forumHost, eventPublisher, speechesPerGuidance, DEFAULT_MAX_ROUNDS);
    }

    ForumTranscriptService(ForumHost forumHost, AnalysisEventPublisher eventPublisher, int speechesPerGuidance, int maxRounds) {
        this.forumHost = forumHost;
        this.eventPublisher = eventPublisher;
        this.speechesPerGuidance = speechesPerGuidance;
        this.maxRounds = maxRounds;
    }

    @Override
    public synchronized void onEvent(AnalysisEvent event) {
        if (!(event instanceof AgentSpeechEvent speechEvent)) {
            return;
        }

        TaskForumState state = stateFor(speechEvent.taskId());
        state.transcript.add(new ForumMessage(
            speechEvent.agentName(),
            "agent",
            speechEvent.content(),
            speechEvent.occurredAt()
        ));

        if (state.transcript.size() % speechesPerGuidance == 0 && !state.converged && state.guidanceHistory.size() < maxRounds) {
            ForumGuidance guidance = forumHost.moderate(state.transcript, state.guidanceHistory.size() + 1);
            ForumGuidance previous = state.guidanceHistory.isEmpty() ? null : state.guidanceHistory.getLast();
            state.guidanceHistory.add(guidance);
            if (detectConvergence(previous, guidance)) {
                state.converged = true;
            }
            eventPublisher.publish(new HostCommentEvent(
                speechEvent.taskId(),
                "ForumHost",
                guidance.promptAddendum(),
                speechEvent.occurredAt()
            ));
        }
    }

    public synchronized ForumGuidance ensureGuidance(String taskId) {
        TaskForumState state = stateFor(taskId);
        if (state.guidanceHistory.isEmpty() && !state.transcript.isEmpty()) {
            ForumGuidance guidance = forumHost.moderate(state.transcript, 1);
            state.guidanceHistory.add(guidance);
            eventPublisher.publish(new HostCommentEvent(
                taskId,
                "ForumHost",
                guidance.promptAddendum(),
                state.transcript.getLast().createdAt()
            ));
        }
        return state.guidanceHistory.isEmpty() ? null : state.guidanceHistory.getLast();
    }

    @Override
    public synchronized List<ForumMessage> transcript(String taskId) {
        return List.copyOf(stateFor(taskId).transcript);
    }

    @Override
    public synchronized List<ForumGuidance> guidanceHistory(String taskId) {
        return List.copyOf(stateFor(taskId).guidanceHistory);
    }

    private TaskForumState stateFor(String taskId) {
        return taskStates.computeIfAbsent(taskId, ignored -> new TaskForumState());
    }

    public synchronized int currentRound(String taskId) {
        return stateFor(taskId).guidanceHistory.size();
    }

    public synchronized boolean hasConverged(String taskId) {
        return stateFor(taskId).converged;
    }

    static boolean detectConvergence(ForumGuidance previous, ForumGuidance current) {
        if (previous == null || current == null) {
            return false;
        }
        long overlap = current.focusPoints().stream()
            .filter(previous.focusPoints()::contains)
            .count();
        double similarity = (double) overlap / Math.max(1, current.focusPoints().size());
        return similarity > CONVERGENCE_THRESHOLD;
    }

    private static final class TaskForumState {

        private final List<ForumMessage> transcript = new ArrayList<>();
        private final List<ForumGuidance> guidanceHistory = new ArrayList<>();
        private boolean converged = false;
    }
}
