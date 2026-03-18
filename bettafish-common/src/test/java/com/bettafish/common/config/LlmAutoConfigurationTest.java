package com.bettafish.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class LlmAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(LlmAutoConfiguration.class);

    @Test
    void createsQueryMediaAndInsightClientsWhenApiKeysAreConfigured() {
        contextRunner.withPropertyValues(
            "bettafish.llm.query.enabled=true",
            "bettafish.llm.query.api-key=query-key",
            "bettafish.llm.media.enabled=true",
            "bettafish.llm.media.api-key=media-key",
            "bettafish.llm.media.base-url=https://llm.example.test/v1",
            "bettafish.llm.media.model-name=gemini-2.5-flash",
            "bettafish.llm.insight.enabled=true",
            "bettafish.llm.insight.api-key=insight-key",
            "bettafish.llm.insight.temperature=0.4"
        ).run(context -> {
            assertThat(context).hasBean("queryChatClient");
            assertThat(context).hasBean("mediaChatClient");
            assertThat(context).hasBean("insightChatClient");
            assertThat(context).hasSingleBean(BettaFishProperties.class);

            assertThat(context).hasBean("queryChatModel");
            assertThat(context).hasBean("mediaChatModel");
            assertThat(context).hasBean("insightChatModel");

            assertThat(context.getBean("queryChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("mediaChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("insightChatClient")).isInstanceOf(ChatClient.class);

            OpenAiChatModel queryModel = context.getBean("queryChatModel", OpenAiChatModel.class);
            OpenAiChatModel mediaModel = context.getBean("mediaChatModel", OpenAiChatModel.class);
            OpenAiChatModel insightModel = context.getBean("insightChatModel", OpenAiChatModel.class);
            BettaFishProperties properties = context.getBean(BettaFishProperties.class);

            assertThat(options(queryModel).getModel()).isEqualTo("deepseek-chat");
            assertThat(options(queryModel).getTemperature()).isEqualTo(0.7);
            assertThat(baseUrl(queryModel)).isEqualTo("https://api.deepseek.com");
            assertThat(properties.getLlm().getQuery().getApiKey()).isEqualTo("query-key");

            assertThat(options(mediaModel).getModel()).isEqualTo("gemini-2.5-flash");
            assertThat(options(mediaModel).getTemperature()).isEqualTo(0.7);
            assertThat(baseUrl(mediaModel)).isEqualTo("https://llm.example.test/v1");
            assertThat(properties.getLlm().getMedia().getModelName()).isEqualTo("gemini-2.5-flash");

            assertThat(options(insightModel).getModel()).isEqualTo("kimi-k2");
            assertThat(options(insightModel).getTemperature()).isEqualTo(0.4);
            assertThat(baseUrl(insightModel)).isEqualTo("https://api.moonshot.cn/v1");
            assertThat(properties.getLlm().getInsight().getTemperature()).isEqualTo(0.4);
        });
    }

    @Test
    void skipsClientAndModelBeansWhenApiKeyIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("queryChatClient");
            assertThat(context).doesNotHaveBean("mediaChatClient");
            assertThat(context).doesNotHaveBean("insightChatClient");
            assertThat(context).doesNotHaveBean("queryChatModel");
            assertThat(context).doesNotHaveBean("mediaChatModel");
            assertThat(context).doesNotHaveBean("insightChatModel");
        });
    }

    @Test
    void skipsClientBeansWhenApiKeyExistsButClientIsDisabled() {
        contextRunner.withPropertyValues(
            "bettafish.llm.query.api-key=query-key",
            "bettafish.llm.media.api-key=media-key",
            "bettafish.llm.insight.api-key=insight-key"
        ).run(context -> {
            assertThat(context).doesNotHaveBean("queryChatClient");
            assertThat(context).doesNotHaveBean("mediaChatClient");
            assertThat(context).doesNotHaveBean("insightChatClient");
            assertThat(context).doesNotHaveBean("queryChatModel");
            assertThat(context).doesNotHaveBean("mediaChatModel");
            assertThat(context).doesNotHaveBean("insightChatModel");
        });
    }

    private static OpenAiChatOptions options(OpenAiChatModel model) {
        return (OpenAiChatOptions) model.getDefaultOptions();
    }

    private static String baseUrl(OpenAiChatModel model) {
        Object openAiApi = ReflectionTestUtils.getField(model, "openAiApi");
        return (String) ReflectionTestUtils.getField(openAiApi, "baseUrl");
    }
}
