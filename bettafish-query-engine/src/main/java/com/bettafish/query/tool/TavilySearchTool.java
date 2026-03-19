package com.bettafish.query.tool;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.SourceReference;

@Component
public class TavilySearchTool {

    public List<SourceReference> search(String query) {
        String token = UUID.nameUUIDFromBytes(query.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
        return List.of(new SourceReference(
            "Tavily stub result for " + query,
            "https://example.com/news/" + token,
            "Placeholder source representing the future Tavily integration for " + query + "."
        ));
    }
}
