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
    void executesEnumNodesThroughSharedRunner() {
        TestNodeContext context = new TestNodeContext();
        StateMachineRunner<TestNode, TestNodeContext> runner = new StateMachineRunner<>();

        runner.run(context, TestNode.START);

        assertEquals(List.of(TestNode.START, TestNode.MIDDLE, TestNode.END), context.visitedNodes());
        assertEquals(TestNode.END, context.getCurrentNode());
    }

    @Test
    void publishesNodeStartedEventsWhenContextHasPublisher() {
        RecordingPublisher publisher = new RecordingPublisher();
        TestNodeContext context = new TestNodeContext("task-1", "QUERY", publisher);
        StateMachineRunner<TestNode, TestNodeContext> runner = new StateMachineRunner<>();

        runner.run(context, TestNode.START);

        assertEquals(3, publisher.events().size());
        assertEquals(
            List.of("START", "MIDDLE", "END"),
            publisher.events().stream()
                .map(NodeStartedEvent.class::cast)
                .map(NodeStartedEvent::nodeName)
                .toList()
        );
        assertEquals(
            List.of("task-1", "task-1", "task-1"),
            publisher.events().stream().map(AnalysisEvent::taskId).toList()
        );
    }

    private enum TestNode implements Node<TestNode, TestNodeContext> {
        START {
            @Override
            public TestNode execute(TestNodeContext context) {
                context.record(this);
                return MIDDLE;
            }
        },
        MIDDLE {
            @Override
            public TestNode execute(TestNodeContext context) {
                context.record(this);
                return END;
            }
        },
        END {
            @Override
            public TestNode execute(TestNodeContext context) {
                context.record(this);
                return null;
            }
        }
    }

    private static final class TestNodeContext extends NodeContext<TestNode> {

        private final List<TestNode> visitedNodes = new ArrayList<>();

        private TestNodeContext() {
        }

        private TestNodeContext(String taskId, String engineName, AnalysisEventPublisher publisher) {
            super(taskId, engineName, publisher);
        }

        private List<TestNode> visitedNodes() {
            return visitedNodes;
        }

        private void record(TestNode node) {
            visitedNodes.add(node);
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
