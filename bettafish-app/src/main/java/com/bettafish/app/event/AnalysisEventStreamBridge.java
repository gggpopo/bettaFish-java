package com.bettafish.app.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.bettafish.common.event.AnalysisEvent;

@Component
public class AnalysisEventStreamBridge {

    private final SimpMessagingTemplate messagingTemplate;

    public AnalysisEventStreamBridge(EventBus eventBus, SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        eventBus.subscribe(this::forward);
    }

    void forward(AnalysisEvent event) {
        messagingTemplate.convertAndSend("/topic/analysis." + event.taskId(), event);
    }
}
