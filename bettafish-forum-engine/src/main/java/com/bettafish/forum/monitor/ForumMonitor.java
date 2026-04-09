package com.bettafish.forum.monitor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.bettafish.forum.ForumHost;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;

@Component
public class ForumMonitor {

    private static final Logger log = LoggerFactory.getLogger(ForumMonitor.class);
    static final int HOST_TRIGGER_THRESHOLD = 5;

    private final ForumHost forumHost;
    private final Map<String, TaskMonitorState> taskStates = new ConcurrentHashMap<>();

    public ForumMonitor(ForumHost forumHost) {
        this.forumHost = forumHost;
    }

    public void onAgentSpeech(String taskId, String agentName, String content) {
        var state = taskStates.computeIfAbsent(taskId, k -> new TaskMonitorState());
        if (shouldFilter(agentName, content)) {
            return;
        }

        synchronized (state) {
            state.speeches.add(new AgentSpeech(agentName, content, Instant.now()));
            state.speechesSinceLastHost++;
            state.lastActivity = Instant.now();

            if (state.speechesSinceLastHost >= HOST_TRIGGER_THRESHOLD) {
                triggerHostSpeech(taskId, state);
            }
        }
    }

    public boolean hasConverged(String taskId) {
        var state = taskStates.get(taskId);
        return state != null && state.converged;
    }

    public MonitorStats getStats(String taskId) {
        var state = taskStates.get(taskId);
        if (state == null) {
            return new MonitorStats(0, 0, false, null);
        }
        synchronized (state) {
            return new MonitorStats(
                state.speeches.size(),
                state.hostSpeechCount,
                state.converged,
                state.lastActivity
            );
        }
    }

    static boolean shouldFilter(String agentName, String content) {
        if (content == null || content.length() < 20) {
            return true;
        }
        if (content.contains("search_query") && !content.contains("summary")) {
            return true;
        }
        return false;
    }

    private void triggerHostSpeech(String taskId, TaskMonitorState state) {
        List<ForumMessage> recentMessages = state.speeches.stream()
            .map(s -> new ForumMessage(s.agentName(), "agent", s.content(), s.timestamp()))
            .toList();

        int round = state.hostSpeechCount + 1;
        try {
            ForumGuidance guidance = forumHost.moderate(recentMessages, round);
            if (state.lastGuidance != null && detectConvergence(state.lastGuidance, guidance)) {
                state.converged = true;
                log.info("Forum discussion converged for task {} at round {}", taskId, round);
            }
            state.lastGuidance = guidance;
        } catch (Exception e) {
            log.warn("Failed to generate host speech for task {}: {}", taskId, e.getMessage());
        }

        state.speechesSinceLastHost = 0;
        state.hostSpeechCount++;
    }

    static boolean detectConvergence(ForumGuidance previous, ForumGuidance current) {
        if (previous == null || current == null) {
            return false;
        }
        long overlap = current.focusPoints().stream()
            .filter(previous.focusPoints()::contains)
            .count();
        double similarity = (double) overlap / Math.max(1, current.focusPoints().size());
        return similarity > 0.6;
    }

    public record MonitorStats(int totalSpeeches, int hostSpeeches, boolean converged, Instant lastActivity) {}

    record AgentSpeech(String agentName, String content, Instant timestamp) {}

    static class TaskMonitorState {
        final List<AgentSpeech> speeches = new ArrayList<>();
        int speechesSinceLastHost = 0;
        int hostSpeechCount = 0;
        Instant lastActivity = Instant.now();
        boolean converged = false;
        ForumGuidance lastGuidance = null;
    }
}
