package com.bettafish.common.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.NodeStartedEvent;

public class NodeContext {

    private final String taskId;
    private final String engineName;
    private final AnalysisEventPublisher eventPublisher;
    private String currentNodeName = "";
    private final List<String> nodeHistory = new ArrayList<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, Object> namedServices = new HashMap<>();
    private final Map<Class<?>, Object> typedServices = new HashMap<>();

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

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public List<String> getNodeHistory() {
        return List.copyOf(nodeHistory);
    }

    public void putAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public <T> T getAttribute(String key, Class<T> type) {
        return castValue(key, attributes.get(key), type);
    }

    public void putService(String key, Object value) {
        namedServices.put(key, value);
    }

    public <T> T getService(String key, Class<T> type) {
        return castValue(key, namedServices.get(key), type);
    }

    public <T> void putService(Class<T> type, T value) {
        typedServices.put(type, value);
    }

    public <T> T getService(Class<T> type) {
        return castValue(type.getName(), typedServices.get(type), type);
    }

    <C extends NodeContext> void enterNode(Node<C> node) {
        currentNodeName = node.name();
        nodeHistory.add(currentNodeName);
        onEnterNode(node);
    }

    protected void onEnterNode(Node<?> node) {
        if (!taskId.isBlank() && !engineName.isBlank()) {
            publish(new NodeStartedEvent(taskId, engineName, node.name(), Instant.now()));
        }
    }

    protected void publish(AnalysisEvent event) {
        eventPublisher.publish(event);
    }

    private <T> T castValue(String key, Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Value for %s is not of type %s".formatted(key, type.getName()));
        }
        return type.cast(value);
    }
}
