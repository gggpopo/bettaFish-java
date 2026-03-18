package com.bettafish.spider;

import java.util.List;
import org.springframework.stereotype.Service;
import com.bettafish.spider.platform.BilibiliCrawler;
import com.bettafish.spider.platform.PlatformCrawler;
import com.bettafish.spider.platform.WeiboCrawler;
import com.bettafish.spider.platform.XiaohongshuCrawler;
import com.bettafish.spider.scheduler.CrawlTaskPlan;

@Service
public class CrawlerService {

    private final List<PlatformCrawler> platformCrawlers = List.of(
        new BilibiliCrawler(),
        new WeiboCrawler(),
        new XiaohongshuCrawler()
    );

    public CrawlTaskPlan planCapture(String query) {
        return new CrawlTaskPlan(
            query,
            platformCrawlers.stream().map(PlatformCrawler::platformName).toList()
        );
    }
}
