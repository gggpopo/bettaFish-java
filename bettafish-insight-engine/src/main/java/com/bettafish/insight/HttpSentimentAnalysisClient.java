package com.bettafish.insight;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.bettafish.common.config.SentimentMcpProperties;

@Service
class HttpSentimentAnalysisClient implements SentimentAnalysisClient {

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

        SentimentResponse response = restClient.post()
            .uri("/api/sentiment/analyze")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SentimentRequest(text))
            .retrieve()
            .body(SentimentResponse.class);

        if (response == null) {
            throw new IllegalStateException("Sentiment MCP response body is empty");
        }

        return new SentimentSignal(response.label(), response.confidence(), true);
    }

    private record SentimentRequest(String text) {
    }

    private record SentimentResponse(String label, double confidence) {
    }
}
