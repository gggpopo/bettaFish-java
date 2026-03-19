package com.bettafish.insight.keyword;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class KeywordOptimizer {

    private final ChatClient keywordOptimizerChatClient;

    public KeywordOptimizer(@Qualifier("keywordOptimizerChatClient") ChatClient keywordOptimizerChatClient) {
        this.keywordOptimizerChatClient = keywordOptimizerChatClient;
    }

    public List<String> optimize(String query) {
        return List.of(query, query + " 评论", query + " 热度");
    }
}
