package com.bettafish.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrawlerServiceTest {

    @Test
    void createsCapturePlanAcrossDefaultPlatforms() {
        CrawlerService crawlerService = new CrawlerService();

        var plan = crawlerService.planCapture("武汉大学樱花季舆情热度");

        assertEquals("武汉大学樱花季舆情热度", plan.query());
        assertEquals(3, plan.targetPlatforms().size());
        assertTrue(plan.targetPlatforms().contains("weibo"));
    }
}
