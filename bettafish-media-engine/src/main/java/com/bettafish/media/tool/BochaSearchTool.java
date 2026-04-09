package com.bettafish.media.tool;

import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ExecutionContextHolder;

@Component
public class BochaSearchTool {

    public List<SourceReference> search(String query) {
        ExecutionContext executionContext = ExecutionContextHolder.current();
        if (executionContext != null) {
            executionContext.throwIfCancellationRequested();
        }
        return List.of(new SourceReference(
            "Bocha stub result",
            "https://example.com/media",
            "Placeholder source representing the future multimodal search integration for " + query + "."
        ));
    }
}
