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
