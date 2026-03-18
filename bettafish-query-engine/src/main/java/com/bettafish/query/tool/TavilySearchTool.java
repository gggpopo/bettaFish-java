package com.bettafish.query.tool;

import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.SourceReference;

@Component
public class TavilySearchTool {

    public List<SourceReference> search(String query) {
        return List.of(new SourceReference(
            "Tavily stub result",
            "https://example.com/news",
            "Placeholder source representing the future Tavily integration for " + query + "."
        ));
    }
}
