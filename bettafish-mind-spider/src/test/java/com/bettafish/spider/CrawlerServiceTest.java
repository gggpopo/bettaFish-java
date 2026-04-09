package com.bettafish.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.bettafish.spider.model.CrawlResult;
import com.bettafish.spider.platform.BilibiliCrawler;
import com.bettafish.spider.platform.PlatformCrawler;
import com.bettafish.spider.platform.WeiboCrawler;
import com.bettafish.spider.platform.XiaohongshuCrawler;
import com.bettafish.spider.topic.TopicExtractor;
import com.bettafish.spider.topic.TopicExtractor.ExtractedTopic;

class CrawlerServiceTest {

    private TopicExtractor topicExtractor;
    private CrawlerService crawlerService;

    @BeforeEach
    void setUp() {
        topicExtractor = mock(TopicExtractor.class);
        when(topicExtractor.extract(anyString(), anyInt())).thenReturn(List.of(
            new ExtractedTopic("测试话题", "测试摘要", List.of("关键词1", "关键词2"), 0.9)
        ));

        List<PlatformCrawler> crawlers = List.of(
            new BilibiliCrawler(),
            new WeiboCrawler(),
            new XiaohongshuCrawler()
        );
        crawlerService = new CrawlerService(topicExtractor, crawlers);
    }

    @Test
    void createsCapturePlanAcrossDefaultPlatforms() {
        var plan = crawlerService.planCapture("武汉大学樱花季舆情热度");

        assertEquals("武汉大学樱花季舆情热度", plan.query());
        assertEquals(3, plan.platforms().size());
        assertTrue(plan.platforms().contains("weibo"));
    }

    @Test
    void crawlReturnsResultsFromAllPlatforms() {
        List<CrawlResult> results = crawlerService.crawl("武汉大学樱花季舆情热度");

        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(r -> r.platform().equals("bilibili")));
        assertTrue(results.stream().anyMatch(r -> r.platform().equals("weibo")));
        assertTrue(results.stream().anyMatch(r -> r.platform().equals("xhs")));
    }

    @Test
    void crawlResultsContainExpectedFields() {
        List<CrawlResult> results = crawlerService.crawl("测试查询");

        assertFalse(results.isEmpty());
        CrawlResult first = results.get(0);
        assertFalse(first.title().isEmpty());
        assertFalse(first.content().isEmpty());
        assertFalse(first.author().isEmpty());
        assertFalse(first.url().isEmpty());
    }
}
