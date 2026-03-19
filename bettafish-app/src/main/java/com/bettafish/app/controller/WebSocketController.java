package com.bettafish.app.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.bettafish.app.event.EventBus;

@Controller
public class WebSocketController {

    private final EventBus eventBus;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(EventBus eventBus, SimpMessagingTemplate messagingTemplate) {
        this.eventBus = eventBus;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/analysis.history")
    public void replayAnalysisHistory(String taskId) {
        messagingTemplate.convertAndSend("/topic/analysis." + taskId + ".history", eventBus.history(taskId));
    }
}
