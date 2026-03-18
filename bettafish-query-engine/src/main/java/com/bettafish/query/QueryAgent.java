package com.bettafish.query;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.bettafish.common.api.AnalysisRequest;
import com.bettafish.common.api.EngineResult;
import com.bettafish.common.api.EngineType;
import com.bettafish.common.engine.AnalysisEngine;
import com.bettafish.query.prompt.QueryPrompts;
import com.bettafish.query.tool.TavilySearchTool;

@Service
public class QueryAgent implements AnalysisEngine {

    private final TavilySearchTool tavilySearchTool;

    public QueryAgent(TavilySearchTool tavilySearchTool) {
        this.tavilySearchTool = tavilySearchTool;
    }

    @Override
    public EngineResult analyze(AnalysisRequest request) {
        var sources = tavilySearchTool.search(request.query());
        return new EngineResult(
            EngineType.QUERY,
            "News pulse for " + request.query(),
            QueryPrompts.FIRST_SEARCH_SYSTEM + " " + request.query(),
            List.of(
                "Tracked recent headlines and press framing",
                "Outlined notable time-based developments",
                "Prepared follow-up questions for deeper retrieval"
            ),
            sources,
            Map.of("mode", "tavily-tool")
        );
    }
}
