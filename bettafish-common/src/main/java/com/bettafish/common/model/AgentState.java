package com.bettafish.common.model;

import java.util.ArrayList;
import java.util.List;

public class AgentState {

    private String agentName;
    private String topic;
    private int round;
    private String status = "IDLE";
    private final List<ParagraphState> paragraphs = new ArrayList<>();
    private final List<ForumMessage> forumMessages = new ArrayList<>();

    public AgentState() {
    }

    public AgentState(String agentName, String topic) {
        this.agentName = agentName;
        this.topic = topic;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ParagraphState> getParagraphs() {
        return paragraphs;
    }

    public List<ForumMessage> getForumMessages() {
        return forumMessages;
    }
}
