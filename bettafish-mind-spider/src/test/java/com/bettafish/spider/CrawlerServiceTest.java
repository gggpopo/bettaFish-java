package com.bettafish.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class CrawlerServiceTest {

    @Test
    void createsCapturePlanAcrossDefaultPlatforms() {
        CrawlerService crawlerService = new CrawlerService(ChatClient.builder(mockMindspiderModel()).build());

        var plan = crawlerService.planCapture("武汉大学樱花季舆情热度");

        assertEquals("武汉大学樱花季舆情热度", plan.query());
        assertEquals(3, plan.targetPlatforms().size());
        assertTrue(plan.targetPlatforms().contains("weibo"));
    }

    private static org.springframework.ai.openai.OpenAiChatModel mockMindspiderModel() {
        return org.springframework.ai.openai.OpenAiChatModel.builder()
            .openAiApi(org.springframework.ai.openai.api.OpenAiApi.builder().apiKey("test").baseUrl("https://example.com").build())
            .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder().model("mindspider-model").build())
            .build();
    }
}
