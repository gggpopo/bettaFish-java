package com.bettafish.forum;

import java.util.List;
import org.springframework.stereotype.Service;
import com.bettafish.common.llm.LlmGateway;
import com.bettafish.common.model.ForumGuidance;
import com.bettafish.common.model.ForumMessage;
import com.bettafish.forum.prompt.ForumPrompts;

@Service
public class ForumHost {

    static final String FORUM_HOST_CLIENT = "forumHostChatClient";

    private final LlmGateway llmGateway;

    public ForumHost(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public ForumGuidance moderate(List<ForumMessage> transcript, int revision) {
        return llmGateway.callJson(
            FORUM_HOST_CLIENT,
            ForumPrompts.HOST_SYSTEM_PROMPT,
            ForumPrompts.buildGuidanceUserPrompt(transcript, revision),
            ForumGuidance.class,
            this::validateGuidance,
            () -> defaultGuidance(transcript, revision)
        );
    }

    private LlmGateway.ValidationResult validateGuidance(ForumGuidance guidance) {
        if (guidance == null) {
            return LlmGateway.ValidationResult.invalid("forum guidance must not be null");
        }
        if (guidance.revision() <= 0) {
            return LlmGateway.ValidationResult.invalid("forum guidance revision must be positive");
        }
        if (guidance.promptAddendum() == null || guidance.promptAddendum().isBlank()) {
            return LlmGateway.ValidationResult.invalid("forum guidance promptAddendum must not be blank");
        }
        return LlmGateway.ValidationResult.valid();
    }

    private ForumGuidance defaultGuidance(List<ForumMessage> transcript, int revision) {
        String summary = transcript.isEmpty()
            ? "主持人暂无指导"
            : "主持人指导-" + revision;
        return new ForumGuidance(
            revision,
            summary,
            List.of("优先梳理最新发言里的共识"),
            List.of("还缺少哪些关键事实"),
            List.of("需要补充可信来源"),
            "请根据论坛主持人的最新共识补写，并优先补证据缺口。"
        );
    }
}
