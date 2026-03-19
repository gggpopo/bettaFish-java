package com.bettafish.forum.prompt;

import java.util.List;
import java.util.stream.Collectors;
import com.bettafish.common.model.ForumMessage;

public final class ForumPrompts {

    public static final String HOST_SYSTEM_PROMPT = """
        你是 ForumEngine 的主持人。
        你要根据多位 agent 的发言 transcript 提炼结构化的论坛主持指导。
        输出 JSON 对象：
        {
          "revision": 1,
          "summary": "...",
          "focusPoints": ["..."],
          "challengeQuestions": ["..."],
          "evidenceGaps": ["..."],
          "promptAddendum": "..."
        }
        """;

    private ForumPrompts() {
    }

    public static String buildGuidanceUserPrompt(List<ForumMessage> transcript, int revision) {
        String transcriptText = transcript.stream()
            .map(message -> "[" + message.role() + "] " + message.speaker() + ": " + message.content())
            .collect(Collectors.joining("\n"));
        return """
            当前主持指导版本：%s
            最新论坛 transcript：
            %s
            请输出新的结构化 ForumGuidance。
            """.formatted(revision, transcriptText);
    }
}
