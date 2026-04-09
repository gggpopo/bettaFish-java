package com.bettafish.forum.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.forum.ForumHost;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForumMonitorTest {

    private StubLlmGateway llmGateway;
    private ForumMonitor monitor;

    @BeforeEach
    void setUp() {
        llmGateway = new StubLlmGateway();
        ForumHost forumHost = new ForumHost(llmGateway);
        monitor = new ForumMonitor(forumHost);
    }

    @Test
    void onAgentSpeech_filtersShortContent() {
        monitor.onAgentSpeech("task-1", "QUERY", "short");
        assertEquals(0, monitor.getStats("task-1").totalSpeeches());
    }

    @Test
    void onAgentSpeech_filtersSearchOnlyContent() {
        monitor.onAgentSpeech("task-1", "QUERY", "search_query: some long search query text here");
        assertEquals(0, monitor.getStats("task-1").totalSpeeches());
    }

    @Test
    void onAgentSpeech_triggersHostAfterThreshold() {
        for (int i = 0; i < ForumMonitor.HOST_TRIGGER_THRESHOLD; i++) {
            monitor.onAgentSpeech("task-1", "AGENT-" + i, "This is a sufficiently long speech content number " + i);
        }
        var stats = monitor.getStats("task-1");
        assertEquals(ForumMonitor.HOST_TRIGGER_THRESHOLD, stats.totalSpeeches());
        assertEquals(1, stats.hostSpeeches());
        assertEquals(1, llmGateway.callCount);
    }

    @Test
    void getStats_returnsCorrectCounts() {
        var emptyStats = monitor.getStats("nonexistent");
        assertEquals(0, emptyStats.totalSpeeches());
        assertEquals(0, emptyStats.hostSpeeches());
        assertFalse(emptyStats.converged());
        assertNull(emptyStats.lastActivity());

        monitor.onAgentSpeech("task-2", "QUERY", "A valid speech with enough content to pass filter");
        monitor.onAgentSpeech("task-2", "MEDIA", "Another valid speech with enough content to pass filter");
        var stats = monitor.getStats("task-2");
        assertEquals(2, stats.totalSpeeches());
        assertEquals(0, stats.hostSpeeches());
        assertFalse(stats.converged());
        assertNotNull(stats.lastActivity());
    }

    @Test
    void hasConverged_initiallyFalse() {
        assertFalse(monitor.hasConverged("task-1"));
        monitor.onAgentSpeech("task-1", "QUERY", "A valid speech with enough content to pass filter");
        assertFalse(monitor.hasConverged("task-1"));
    }

    @Test
    void detectConvergence_returnsTrueForSimilarGuidance() {
        var g1 = new ForumGuidance(1, "s", List.of("A", "B", "C"), List.of(), List.of(), "p");
        var g2 = new ForumGuidance(2, "s", List.of("A", "B", "D"), List.of(), List.of(), "p");
        assertTrue(ForumMonitor.detectConvergence(g1, g2));
    }

    @Test
    void detectConvergence_returnsFalseForDifferentGuidance() {
        var g1 = new ForumGuidance(1, "s", List.of("A", "B", "C"), List.of(), List.of(), "p");
        var g2 = new ForumGuidance(2, "s", List.of("X", "Y", "Z"), List.of(), List.of(), "p");
        assertFalse(ForumMonitor.detectConvergence(g1, g2));
    }

    @Test
    void detectConvergence_returnsFalseForNull() {
        var g = new ForumGuidance(1, "s", List.of("A"), List.of(), List.of(), "p");
        assertFalse(ForumMonitor.detectConvergence(null, g));
        assertFalse(ForumMonitor.detectConvergence(g, null));
    }

    private static final class StubLlmGateway implements LlmGateway {
        int callCount = 0;

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              Class<T> responseType, Supplier<T> fallbackSupplier) {
            callCount++;
            if (responseType.equals(ForumGuidance.class)) {
                return responseType.cast(new ForumGuidance(
                    callCount, "guidance-" + callCount,
                    List.of("focus-" + callCount), List.of("q-" + callCount),
                    List.of("gap-" + callCount), "addendum-" + callCount
                ));
            }
            throw new IllegalArgumentException("Unexpected type: " + responseType);
        }

        @Override
        public <T> T callJson(String clientName, String systemPrompt, String userPrompt,
                              TypeReference<T> responseType, Supplier<T> fallbackSupplier) {
            throw new UnsupportedOperationException();
        }
    }
}
