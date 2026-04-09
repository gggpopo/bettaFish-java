package com.bettafish.forum.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.forum.ForumHost;
import com.bettafish.common.model.ForumGuidance;

class ForumMonitorIntegrationTest {

    @Test
    void fullDebateFlow_triggersHostAndDetectsConvergence() {
        ForumHost forumHost = mock(ForumHost.class);
        when(forumHost.moderate(any(), anyInt())).thenReturn(
            new ForumGuidance(1, "Round 1", List.of("point-A", "point-B"), List.of(), List.of(), "addendum"),
            new ForumGuidance(2, "Round 2", List.of("point-A", "point-B"), List.of(), List.of(), "addendum")
        );
        ForumMonitor monitor = new ForumMonitor(forumHost);
        String taskId = "task-debate";

        for (int i = 0; i < 5; i++) {
            monitor.onAgentSpeech(taskId, "agent-" + i,
                "This is a sufficiently long speech content for agent " + i + " to pass the filter.");
        }
        ForumMonitor.MonitorStats statsAfterFirst = monitor.getStats(taskId);
        assertThat(statsAfterFirst.totalSpeeches()).isEqualTo(5);
        assertThat(statsAfterFirst.hostSpeeches()).isEqualTo(1);

        for (int i = 5; i < 10; i++) {
            monitor.onAgentSpeech(taskId, "agent-" + i,
                "This is a sufficiently long speech content for agent " + i + " to pass the filter.");
        }
        ForumMonitor.MonitorStats statsAfterSecond = monitor.getStats(taskId);
        assertThat(statsAfterSecond.totalSpeeches()).isEqualTo(10);
        assertThat(statsAfterSecond.hostSpeeches()).isEqualTo(2);
        assertThat(statsAfterSecond.converged()).isTrue();
    }

    @Test
    void mixedAgentSpeeches_correctlyFiltered() {
        ForumHost forumHost = mock(ForumHost.class);
        when(forumHost.moderate(any(), anyInt())).thenReturn(
            new ForumGuidance(1, "Summary", List.of("focus"), List.of(), List.of(), "addendum")
        );
        ForumMonitor monitor = new ForumMonitor(forumHost);
        String taskId = "task-mixed";

        // Valid speech
        monitor.onAgentSpeech(taskId, "agent-1",
            "This is a sufficiently long speech content that should pass the filter check.");
        // Too short — should be filtered
        monitor.onAgentSpeech(taskId, "agent-2", "short");
        // Search-only content — should be filtered
        monitor.onAgentSpeech(taskId, "agent-3",
            "search_query: some query without summary keyword present in the text");
        // Valid speech
        monitor.onAgentSpeech(taskId, "agent-4",
            "Another sufficiently long speech content that should pass the filter check.");

        ForumMonitor.MonitorStats stats = monitor.getStats(taskId);
        assertThat(stats.totalSpeeches()).isEqualTo(2);
    }
}
