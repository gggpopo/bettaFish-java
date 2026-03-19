package com.bettafish.common.engine;

import java.util.List;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;

public interface ForumGuidanceProvider {

    ForumGuidanceProvider NOOP = new ForumGuidanceProvider() {
        @Override
        public List<ForumMessage> transcript(String taskId) {
            return List.of();
        }

        @Override
        public List<ForumGuidance> guidanceHistory(String taskId) {
            return List.of();
        }
    };

    List<ForumMessage> transcript(String taskId);

    List<ForumGuidance> guidanceHistory(String taskId);

    default ForumGuidance latestGuidance(String taskId) {
        List<ForumGuidance> history = guidanceHistory(taskId);
        return history.isEmpty() ? null : history.getLast();
    }

    static ForumGuidanceProvider noop() {
        return NOOP;
    }
}
