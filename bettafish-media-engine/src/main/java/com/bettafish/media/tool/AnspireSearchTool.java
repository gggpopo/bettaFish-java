package com.bettafish.media.tool;

import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.SourceReference;

@Component
public class AnspireSearchTool {

    public List<SourceReference> search(String query) {
        return List.of(new SourceReference(
            "Anspire stub result",
            "https://example.com/anspire",
            "Placeholder source representing the future Anspire integration for " + query + "."
        ));
    }
}
