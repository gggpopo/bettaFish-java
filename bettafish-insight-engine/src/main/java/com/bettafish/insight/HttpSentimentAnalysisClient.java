package com.bettafish.insight;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.bettafish.common.engine.BlockingCallGuard;
import com.bettafish.common.engine.ExecutionContext;
import com.bettafish.common.engine.ExecutionContextHolder;
import com.bettafish.common.config.SentimentMcpProperties;

@Service
class HttpSentimentAnalysisClient implements SentimentAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(HttpSentimentAnalysisClient.class);

    private final RestClient restClient;
    private final SentimentMcpProperties properties;

    HttpSentimentAnalysisClient(RestClient.Builder restClientBuilder, SentimentMcpProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public SentimentSignal analyze(String text) {
        if (!properties.isEnabled()) {
            return new SentimentSignal("DISABLED", 0.0, false);
        }

        ExecutionContext executionContext = ExecutionContextHolder.current();
        if (executionContext != null) {
            executionContext.throwIfCancellationRequested();
        }

        SentimentResponse response = BlockingCallGuard.call("Sentiment MCP analyze", executionContext, () ->
            restClient.post()
                .uri("/api/sentiment/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SentimentRequest(text))
                .retrieve()
                .body(SentimentResponse.class)
        );

        if (response == null) {
            throw new IllegalStateException("Sentiment MCP response body is empty");
        }

        return new SentimentSignal(response.label(), response.confidence(), true);
    }

    @Override
    public List<SentimentSignal> analyzeBatch(List<String> texts) {
        if (!properties.isEnabled()) {
            return texts.stream().map(t -> new SentimentSignal("DISABLED", 0.0, false)).toList();
        }

        ExecutionContext executionContext = ExecutionContextHolder.current();
        if (executionContext != null) {
            executionContext.throwIfCancellationRequested();
        }

        try {
            List<SentimentResponse> responses = BlockingCallGuard.call("Sentiment MCP analyze-batch", executionContext, () ->
                restClient.post()
                    .uri("/api/sentiment/analyze-batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BatchRequest(texts))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<SentimentResponse>>() {})
            );

            if (responses == null) {
                throw new IllegalStateException("Sentiment MCP batch response body is empty");
            }

            return responses.stream()
                .map(r -> new SentimentSignal(r.label(), r.confidence(), true))
                .toList();
        } catch (Exception e) {
            log.warn("Batch sentiment analysis failed, falling back to sequential", e);
            return texts.stream().map(this::analyze).toList();
        }
    }

    private record SentimentRequest(String text) {
    }

    private record BatchRequest(List<String> texts) {
    }

    private record SentimentResponse(String label, double confidence) {
    }
}
