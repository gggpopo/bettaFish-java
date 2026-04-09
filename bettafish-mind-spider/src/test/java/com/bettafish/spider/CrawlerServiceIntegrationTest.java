package com.bettafish.spider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.bettafish.spider.model.CrawlResult;
import com.bettafish.spider.platform.BilibiliCrawler;
import com.bettafish.spider.platform.PlatformCrawler;
import com.bettafish.spider.platform.WeiboCrawler;
import com.bettafish.spider.platform.XiaohongshuCrawler;
import com.bettafish.spider.topic.TopicExtractor;
import com.bettafish.spider.topic.TopicExtractor.ExtractedTopic;

class CrawlerServiceIntegrationTest {

    @Test
    void crawl_dispatchesToAllPlatforms() {
        TopicExtractor topicExtractor = mock(TopicExtractor.class);
        when(topicExtractor.extract(anyString(), anyInt())).thenReturn(List.of(
            new ExtractedTopic("话题A", "摘要A", List.of("关键词1"), 0.95),
            new ExtractedTopic("话题B", "摘要B", List.of("关键词2"), 0.85)
        ));
        List<PlatformCrawler> crawlers = List.of(
            new BilibiliCrawler(), new WeiboCrawler(), new XiaohongshuCrawler()
        );
        CrawlerService service = new CrawlerService(topicExtractor, crawlers);

        List<CrawlResult> results = service.crawl("测试查询");

        assertThat(results).hasSize(3);
        assertThat(results.stream().map(CrawlResult::platform).toList())
            .containsExactlyInAnyOrder("bilibili", "weibo", "xhs");
    }

    @Test
    void crawl_handlesEmptyTopicExtraction() {
        TopicExtractor topicExtractor = mock(TopicExtractor.class);
        when(topicExtractor.extract(anyString(), anyInt())).thenReturn(List.of());
        List<PlatformCrawler> crawlers = List.of(
            new BilibiliCrawler(), new WeiboCrawler(), new XiaohongshuCrawler()
        );
        CrawlerService service = new CrawlerService(topicExtractor, crawlers);

        List<CrawlResult> results = service.crawl("原始查询");

        assertThat(results).isNotEmpty();
        assertThat(results).hasSize(3);
    }
}
