package com.bettafish.query.tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.bettafish.common.api.SourceReference;
import com.bettafish.common.config.BettaFishProperties;
import com.bettafish.common.util.RetryHelper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TavilySearchTool {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchTool.class);
    private static final Duration RETRY_DELAY = Duration.ofMillis(200);
    private static final int RETRY_ATTEMPTS = 2;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI searchUri;
    private final String apiKey;
    private final int maxResults;

    @Autowired
    public TavilySearchTool(BettaFishProperties properties, ObjectMapper objectMapper) {
        this(
            HttpClient.newHttpClient(),
            objectMapper,
            properties.getTavily().getBaseUrl(),
            properties.getTavily().getApiKey(),
            properties.getTavily().getMaxResults()
        );
    }

    public TavilySearchTool(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String apiKey, int maxResults) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.searchUri = URI.create(normalizeBaseUrl(baseUrl));
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxResults = Math.max(1, maxResults);
    }

    public List<SourceReference> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (apiKey.isBlank()) {
            log.warn("Skipping Tavily search because apiKey is blank");
            return List.of();
        }
        try {
            return RetryHelper.withRetry(RETRY_ATTEMPTS, RETRY_DELAY, () -> doSearch(query));
        } catch (RuntimeException ex) {
            log.warn("Tavily search failed for query={}: {}", query, ex.getMessage());
            return List.of();
        }
    }

    private List<SourceReference> doSearch(String query) {
        try {
            String payload = objectMapper.writeValueAsString(new TavilySearchRequest(apiKey, query, maxResults));
            HttpRequest request = HttpRequest.newBuilder(searchUri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Tavily returned HTTP " + response.statusCode() + " body=" + response.body());
            }

            TavilySearchResponse searchResponse = objectMapper.readValue(response.body(), TavilySearchResponse.class);
            if (searchResponse.results() == null) {
                return List.of();
            }

            return searchResponse.results().stream()
                .filter(result -> result != null && !isBlank(result.url()) && !isBlank(result.title()))
                .map(result -> new SourceReference(
                    result.title().trim(),
                    result.url().trim(),
                    isBlank(result.content()) ? "" : result.content().trim()
                ))
                .limit(maxResults)
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Tavily response", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tavily request interrupted", ex);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "https://api.tavily.com" : baseUrl.trim();
        if (normalized.endsWith("/search")) {
            return normalized;
        }
        return normalized.replaceAll("/+$", "") + "/search";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TavilySearchRequest(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("query") String query,
        @JsonProperty("max_results") int maxResults
    ) {
    }

    private record TavilySearchResponse(List<TavilySearchResult> results) {
    }

    private record TavilySearchResult(String title, String url, String content) {
    }
}
