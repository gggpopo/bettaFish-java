package com.bettafish.common.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.common.event.AnalysisEvent;
import com.bettafish.common.event.AnalysisEventPublisher;
import com.bettafish.common.event.NodeStartedEvent;

class StateMachineRunnerTest {

    @Test
    void executesObjectNodesThroughSharedRunner() {
        TestNodeContext context = new TestNodeContext();
        StateMachineRunner<TestNodeContext> runner = new StateMachineRunner<>();

        runner.run(context, new StartNode());

        assertEquals(List.of("StartNode", "MiddleNode", "EndNode"), context.visitedNodes());
        assertEquals("EndNode", context.getCurrentNodeName());
        assertEquals("value-from-service", context.getService("llm", String.class));
        assertEquals("value-from-attribute", context.getAttribute("mode", String.class));
    }

    @Test
    void publishesNodeStartedEventsWhenContextHasPublisher() {
        RecordingPublisher publisher = new RecordingPublisher();
        TestNodeContext context = new TestNodeContext("task-1", "QUERY", publisher);
        StateMachineRunner<TestNodeContext> runner = new StateMachineRunner<>();

        runner.run(context, new StartNode());

        assertEquals(3, publisher.events().size());
        assertEquals(
            List.of("StartNode", "MiddleNode", "EndNode"),
            publisher.events().stream()
                .map(NodeStartedEvent.class::cast)
                .map(NodeStartedEvent::nodeName)
                .toList()
        );
    }

    private static final class StartNode implements Node<TestNodeContext> {

        @Override
        public String name() {
            return "StartNode";
        }

        @Override
        public Node<TestNodeContext> execute(TestNodeContext context) {
            context.record(name());
            context.putService("llm", "value-from-service");
            context.putAttribute("mode", "value-from-attribute");
            return new MiddleNode();
        }
    }

    private static final class MiddleNode implements Node<TestNodeContext> {

        @Override
        public String name() {
            return "MiddleNode";
        }

        @Override
        public Node<TestNodeContext> execute(TestNodeContext context) {
            context.record(name());
            return new EndNode();
        }
    }

    private static final class EndNode implements Node<TestNodeContext> {

        @Override
        public String name() {
            return "EndNode";
        }

        @Override
        public Node<TestNodeContext> execute(TestNodeContext context) {
            context.record(name());
            return null;
        }
    }

    private static final class TestNodeContext extends NodeContext {

        private final List<String> visitedNodes = new ArrayList<>();

        private TestNodeContext() {
        }

        private TestNodeContext(String taskId, String engineName, AnalysisEventPublisher publisher) {
            super(taskId, engineName, publisher);
        }

        private List<String> visitedNodes() {
            return visitedNodes;
        }

        private void record(String nodeName) {
            visitedNodes.add(nodeName);
        }
    }

    private static final class RecordingPublisher implements AnalysisEventPublisher {

        private final List<AnalysisEvent> events = new ArrayList<>();

        @Override
        public void publish(AnalysisEvent event) {
            events.add(event);
        }

        private List<AnalysisEvent> events() {
            return events;
        }
    }
}
