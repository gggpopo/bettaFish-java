package com.bettafish.app.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import com.bettafish.common.event.EngineStartedEvent;

class AnalysisEventStreamBridgeTest {

    private RecordingMessageChannel messageChannel;

    @BeforeEach
    void setUp() {
        messageChannel = new RecordingMessageChannel();
    }

    @Test
    void forwardsPublishedEventsToTaskScopedTopic() {
        InMemoryEventBus eventBus = new InMemoryEventBus();
        SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(messageChannel);
        new AnalysisEventStreamBridge(eventBus, messagingTemplate);
        EngineStartedEvent event = new EngineStartedEvent("task-1", "QUERY", Instant.parse("2026-03-18T00:00:00Z"));

        eventBus.publish(event);

        Message<?> message = messageChannel.lastMessage();
        assertNotNull(message);
        assertEquals("/topic/analysis.task-1", SimpMessageHeaderAccessor.getDestination(message.getHeaders()));
        assertEquals(event, message.getPayload());
    }

    private static final class RecordingMessageChannel implements MessageChannel {

        private Message<?> lastMessage;

        @Override
        public boolean send(Message<?> message) {
            lastMessage = message;
            return true;
        }

        @Override
        public boolean send(Message<?> message, long timeout) {
            return send(message);
        }

        private Message<?> lastMessage() {
            return lastMessage;
        }
    }
}
