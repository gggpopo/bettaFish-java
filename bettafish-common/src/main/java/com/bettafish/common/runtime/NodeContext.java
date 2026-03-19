package com.bettafish.common.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.NodeStartedEvent;

public class NodeContext<N extends Enum<N>> {

    private final String taskId;
    private final String engineName;
    private final AnalysisEventPublisher eventPublisher;
    private N currentNode;
    private final List<N> nodeHistory = new ArrayList<>();
    private final Map<String, Object> attributes = new HashMap<>();

    protected NodeContext() {
        this("", "", AnalysisEventPublisher.noop());
    }

    protected NodeContext(String taskId, String engineName, AnalysisEventPublisher eventPublisher) {
        this.taskId = taskId == null ? "" : taskId;
        this.engineName = engineName == null ? "" : engineName;
        this.eventPublisher = eventPublisher == null ? AnalysisEventPublisher.noop() : eventPublisher;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getEngineName() {
        return engineName;
    }

    public AnalysisEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public N getCurrentNode() {
        return currentNode;
    }

    public List<N> getNodeHistory() {
        return nodeHistory;
    }

    public void putAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Attribute %s is not of type %s".formatted(key, type.getName()));
        }
        return type.cast(value);
    }

    void enterNode(N node) {
        currentNode = node;
        nodeHistory.add(node);
        onEnterNode(node);
    }

    protected void onEnterNode(N node) {
        if (!taskId.isBlank() && !engineName.isBlank()) {
            publish(new NodeStartedEvent(taskId, engineName, node.name(), Instant.now()));
        }
    }

    protected void publish(AnalysisEvent event) {
        eventPublisher.publish(event);
    }
}
