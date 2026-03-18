package com.bettafish.app.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    @MessageMapping("/analysis.events")
    @SendTo("/topic/analysis.events")
    public String streamAnalysisEvent(String payload) {
        return payload;
    }
}
