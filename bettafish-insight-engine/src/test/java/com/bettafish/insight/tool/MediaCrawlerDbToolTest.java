package com.bettafish.insight.tool;

import com.bettafish.common.config.DatabaseSearchProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MediaCrawlerDbToolTest {

    @Test
    void search_whenDisabled_returnsStubResult() {
        var props = new DatabaseSearchProperties();
        props.setEnabled(false);
        var tool = new MediaCrawlerDbTool(props);
        var results = tool.search("test", List.of("keyword"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).contains("stub");
    }

    @Test
    void searchHotContent_whenDisabled_returnsEmpty() {
        var props = new DatabaseSearchProperties();
        props.setEnabled(false);
        var tool = new MediaCrawlerDbTool(props);
        assertThat(tool.searchHotContent("24h", 10)).isEmpty();
    }

    @Test
    void searchTopicGlobally_whenDisabled_returnsEmpty() {
        var props = new DatabaseSearchProperties();
        props.setEnabled(false);
        var tool = new MediaCrawlerDbTool(props);
        assertThat(tool.searchTopicGlobally("test", List.of(), 50)).isEmpty();
    }

    @Test
    void getCommentsForTopic_whenDisabled_returnsEmpty() {
        var props = new DatabaseSearchProperties();
        props.setEnabled(false);
        var tool = new MediaCrawlerDbTool(props);
        assertThat(tool.getCommentsForTopic("test", List.of(), 50)).isEmpty();
    }
}
