package com.bettafish.media.tool;

import java.util.List;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ExecutionContextHolder;

@Component
public class AnspireSearchTool {

    public List<SourceReference> search(String query) {
        ExecutionContext executionContext = ExecutionContextHolder.current();
        if (executionContext != null) {
            executionContext.throwIfCancellationRequested();
        }
        return List.of(new SourceReference(
            "Anspire stub result",
            "https://example.com/anspire",
            "Placeholder source representing the future Anspire integration for " + query + "."
        ));
    }
}
