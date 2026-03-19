package com.bettafish.media;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.media.prompt.MediaPrompts;
import com.bettafish.media.tool.BochaSearchTool;

@Service
public class MediaAgent implements AnalysisEngine {

    private final BochaSearchTool bochaSearchTool;

    public MediaAgent(BochaSearchTool bochaSearchTool) {
        this.bochaSearchTool = bochaSearchTool;
    }

    @Override
    public EngineResult analyze(AnalysisRequest request) {
        var sources = bochaSearchTool.search(request.query());
        return new EngineResult(
            EngineType.MEDIA,
            "Multimodal coverage for " + request.query(),
            MediaPrompts.FIRST_SEARCH_SYSTEM + " " + request.query() + ".",
            List.of(
                "Estimated how image-heavy the topic appears",
                "Highlighted likely social sharing angles",
                "Captured placeholder structured facts for later enrichment"
            ),
            sources,
            Map.of("mode", "bocha-tool")
        );
    }

    @Override
    public String engineName() {
        return EngineType.MEDIA.name();
    }
}
