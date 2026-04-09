package com.bettafish.spider.topic;

import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.bettafish.common.llm.LlmGateway;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class TopicExtractor {

    private static final Logger log = LoggerFactory.getLogger(TopicExtractor.class);

    private static final String SYSTEM_PROMPT = """
        你是话题提取专家。从新闻内容中提取热门话题，返回JSON数组。
        每个话题包含: title(标题), summary(摘要), keywords(关键词列表), relevanceScore(相关度0-1)。
        按相关度降序排列，只返回JSON数组，不要其他内容。
        """;

    private final LlmGateway llmGateway;

    public TopicExtractor(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public record ExtractedTopic(String title, String summary, List<String> keywords, double relevanceScore) {}

    public List<ExtractedTopic> extract(String newsContent, int maxTopics) {
        log.info("Extracting up to {} topics from news content (length={})", maxTopics, newsContent.length());
        String userPrompt = "请从以下内容中提取最多%d个热门话题:\n\n%s".formatted(maxTopics, newsContent);

        Supplier<List<ExtractedTopic>> fallback = () -> List.of(
            new ExtractedTopic("默认话题", newsContent.substring(0, Math.min(100, newsContent.length())),
                List.of(newsContent.split("\\s+")[0]), 0.5)
        );

        return llmGateway.callJson("mindspider", SYSTEM_PROMPT, userPrompt,
            new TypeReference<List<ExtractedTopic>>() {}, fallback);
    }
}
