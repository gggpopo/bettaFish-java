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
    void createsAllDedicatedClientsWhenApiKeysAreConfigured() {
        contextRunner.withPropertyValues(
            "bettafish.llm.query.enabled=true",
            "bettafish.llm.query.api-key=query-key",
            "bettafish.llm.media.enabled=true",
            "bettafish.llm.media.api-key=media-key",
            "bettafish.llm.media.base-url=https://llm.example.test/v1",
            "bettafish.llm.media.model-name=gemini-2.5-flash",
            "bettafish.llm.insight.enabled=true",
            "bettafish.llm.insight.api-key=insight-key",
            "bettafish.llm.insight.temperature=0.4",
            "bettafish.llm.report.enabled=true",
            "bettafish.llm.report.api-key=report-key",
            "bettafish.llm.forum-host.enabled=true",
            "bettafish.llm.forum-host.api-key=forum-key",
            "bettafish.llm.keyword-optimizer.enabled=true",
            "bettafish.llm.keyword-optimizer.api-key=keyword-key",
            "bettafish.llm.mindspider.enabled=true",
            "bettafish.llm.mindspider.api-key=mindspider-key"
        ).run(context -> {
            assertThat(context).hasBean("queryChatClient");
            assertThat(context).hasBean("mediaChatClient");
            assertThat(context).hasBean("insightChatClient");
            assertThat(context).hasBean("reportChatClient");
            assertThat(context).hasBean("forumHostChatClient");
            assertThat(context).hasBean("keywordOptimizerChatClient");
            assertThat(context).hasBean("mindspiderChatClient");
            assertThat(context).hasSingleBean(BettaFishProperties.class);

            assertThat(context).hasBean("queryChatModel");
            assertThat(context).hasBean("mediaChatModel");
            assertThat(context).hasBean("insightChatModel");
            assertThat(context).hasBean("reportChatModel");
            assertThat(context).hasBean("forumHostChatModel");
            assertThat(context).hasBean("keywordOptimizerChatModel");
            assertThat(context).hasBean("mindspiderChatModel");

            assertThat(context.getBean("queryChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("mediaChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("insightChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("reportChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("forumHostChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("keywordOptimizerChatClient")).isInstanceOf(ChatClient.class);
            assertThat(context.getBean("mindspiderChatClient")).isInstanceOf(ChatClient.class);

            OpenAiChatModel queryModel = context.getBean("queryChatModel", OpenAiChatModel.class);
            OpenAiChatModel mediaModel = context.getBean("mediaChatModel", OpenAiChatModel.class);
            OpenAiChatModel insightModel = context.getBean("insightChatModel", OpenAiChatModel.class);
            OpenAiChatModel reportModel = context.getBean("reportChatModel", OpenAiChatModel.class);
            OpenAiChatModel forumHostModel = context.getBean("forumHostChatModel", OpenAiChatModel.class);
            OpenAiChatModel keywordOptimizerModel = context.getBean("keywordOptimizerChatModel", OpenAiChatModel.class);
            OpenAiChatModel mindspiderModel = context.getBean("mindspiderChatModel", OpenAiChatModel.class);
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

            assertThat(options(reportModel).getModel()).isEqualTo("gemini-2.5-pro");
            assertThat(options(forumHostModel).getModel()).isEqualTo("Qwen/Qwen3-235B-A22B");
            assertThat(options(keywordOptimizerModel).getModel()).isEqualTo("Qwen/Qwen3-235B-A22B");
            assertThat(options(mindspiderModel).getModel()).isEqualTo("deepseek-chat");
        });
    }

    @Test
    void skipsClientAndModelBeansWhenApiKeyIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("queryChatClient");
            assertThat(context).doesNotHaveBean("mediaChatClient");
            assertThat(context).doesNotHaveBean("insightChatClient");
            assertThat(context).doesNotHaveBean("reportChatClient");
            assertThat(context).doesNotHaveBean("forumHostChatClient");
            assertThat(context).doesNotHaveBean("keywordOptimizerChatClient");
            assertThat(context).doesNotHaveBean("mindspiderChatClient");
            assertThat(context).doesNotHaveBean("queryChatModel");
            assertThat(context).doesNotHaveBean("mediaChatModel");
            assertThat(context).doesNotHaveBean("insightChatModel");
            assertThat(context).doesNotHaveBean("reportChatModel");
            assertThat(context).doesNotHaveBean("forumHostChatModel");
            assertThat(context).doesNotHaveBean("keywordOptimizerChatModel");
            assertThat(context).doesNotHaveBean("mindspiderChatModel");
        });
    }

    @Test
    void skipsClientBeansWhenApiKeyExistsButClientIsDisabled() {
        contextRunner.withPropertyValues(
            "bettafish.llm.query.api-key=query-key",
            "bettafish.llm.media.api-key=media-key",
            "bettafish.llm.insight.api-key=insight-key",
            "bettafish.llm.report.api-key=report-key",
            "bettafish.llm.forum-host.api-key=forum-key",
            "bettafish.llm.keyword-optimizer.api-key=keyword-key",
            "bettafish.llm.mindspider.api-key=mindspider-key"
        ).run(context -> {
            assertThat(context).doesNotHaveBean("queryChatClient");
            assertThat(context).doesNotHaveBean("mediaChatClient");
            assertThat(context).doesNotHaveBean("insightChatClient");
            assertThat(context).doesNotHaveBean("reportChatClient");
            assertThat(context).doesNotHaveBean("forumHostChatClient");
            assertThat(context).doesNotHaveBean("keywordOptimizerChatClient");
            assertThat(context).doesNotHaveBean("mindspiderChatClient");
            assertThat(context).doesNotHaveBean("queryChatModel");
            assertThat(context).doesNotHaveBean("mediaChatModel");
            assertThat(context).doesNotHaveBean("insightChatModel");
            assertThat(context).doesNotHaveBean("reportChatModel");
            assertThat(context).doesNotHaveBean("forumHostChatModel");
            assertThat(context).doesNotHaveBean("keywordOptimizerChatModel");
            assertThat(context).doesNotHaveBean("mindspiderChatModel");
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
