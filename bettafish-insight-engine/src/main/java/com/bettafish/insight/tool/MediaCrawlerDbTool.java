package com.bettafish.insight.tool;

import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.SourceReference;

@Component
public class MediaCrawlerDbTool {

    public List<SourceReference> search(String query, List<String> keywords) {
        return List.of(new SourceReference(
            "Media crawler DB stub result",
            "https://example.com/insight",
            "Placeholder source representing the future database integration for "
                + query + " with keywords " + String.join(", ", keywords) + "."
        ));
    }
}
