package com.bettafish.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BettaFishProperties.class)
public class LlmAutoConfiguration {

    private final BettaFishProperties properties;

    public LlmAutoConfiguration(BettaFishProperties properties) {
        this.properties = properties;
    }

    @Bean("queryChatModel")
    @ConditionalOnProperty(prefix = "bettafish.llm.query", name = "enabled", havingValue = "true")
    public OpenAiChatModel queryChatModel() {
        return buildChatModel(properties.getLlm().getQuery());
    }

    @Bean("queryChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.query", name = "enabled", havingValue = "true")
    public ChatClient queryChatClient(@Qualifier("queryChatModel") OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("mediaChatModel")
    @ConditionalOnProperty(prefix = "bettafish.llm.media", name = "enabled", havingValue = "true")
    public OpenAiChatModel mediaChatModel() {
        return buildChatModel(properties.getLlm().getMedia());
    }

    @Bean("mediaChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.media", name = "enabled", havingValue = "true")
    public ChatClient mediaChatClient(@Qualifier("mediaChatModel") OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("insightChatModel")
    @ConditionalOnProperty(prefix = "bettafish.llm.insight", name = "enabled", havingValue = "true")
    public OpenAiChatModel insightChatModel() {
        return buildChatModel(properties.getLlm().getInsight());
    }

    @Bean("insightChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.insight", name = "enabled", havingValue = "true")
    public ChatClient insightChatClient(@Qualifier("insightChatModel") OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("reportChatModel")
    @ConditionalOnProperty(prefix = "bettafish.llm.report", name = "enabled", havingValue = "true")
    public OpenAiChatModel reportChatModel() {
        return buildChatModel(properties.getLlm().getReport());
    }

    @Bean("reportChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.report", name = "enabled", havingValue = "true")
    public ChatClient reportChatClient(@Qualifier("reportChatModel") OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("forumHostChatModel")
    @ConditionalOnProperty(prefix = "bettafish.llm.forum-host", name = "enabled", havingValue = "true")
    public OpenAiChatModel forumHostChatModel() {
        return buildChatModel(properties.getLlm().getForumHost());
    }

    @Bean("forumHostChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.forum-host", name = "enabled", havingValue = "true")
    public ChatClient forumHostChatClient(@Qualifier("forumHostChatModel") OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("keywordOptimizerChatModel")
    @ConditionalOnProperty(prefix = "bettafish.llm.keyword-optimizer", name = "enabled", havingValue = "true")
    public OpenAiChatModel keywordOptimizerChatModel() {
        return buildChatModel(properties.getLlm().getKeywordOptimizer());
    }

    @Bean("keywordOptimizerChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.keyword-optimizer", name = "enabled", havingValue = "true")
    public ChatClient keywordOptimizerChatClient(@Qualifier("keywordOptimizerChatModel") OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("mindspiderChatModel")
    @ConditionalOnProperty(prefix = "bettafish.llm.mindspider", name = "enabled", havingValue = "true")
    public OpenAiChatModel mindspiderChatModel() {
        return buildChatModel(properties.getLlm().getMindspider());
    }

    @Bean("mindspiderChatClient")
    @ConditionalOnProperty(prefix = "bettafish.llm.mindspider", name = "enabled", havingValue = "true")
    public ChatClient mindspiderChatClient(@Qualifier("mindspiderChatModel") OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    private OpenAiChatModel buildChatModel(BettaFishProperties.ClientConfig config) {
        OpenAiApi openAiApi = OpenAiApi.builder()
            .apiKey(config.getApiKey())
            .baseUrl(config.getBaseUrl())
            .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
            .model(config.getModelName())
            .temperature(config.getTemperature())
            .build();

        return OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(defaultOptions)
            .build();
    }
}
