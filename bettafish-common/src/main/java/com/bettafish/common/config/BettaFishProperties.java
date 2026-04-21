package com.bettafish.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bettafish")
public class BettaFishProperties {

    private final LlmProperties llm = new LlmProperties();
    private final TavilyConfig tavily = new TavilyConfig();
    private final BochaConfig bocha = new BochaConfig();
    private final DatabaseSearchConfig databaseSearch = new DatabaseSearchConfig();
    private final SentimentMcpConfig sentimentMcp = new SentimentMcpConfig();

    public LlmProperties getLlm() {
        return llm;
    }

    public TavilyConfig getTavily() {
        return tavily;
    }

    public BochaConfig getBocha() {
        return bocha;
    }

    public DatabaseSearchConfig getDatabaseSearch() {
        return databaseSearch;
    }

    public SentimentMcpConfig getSentimentMcp() {
        return sentimentMcp;
    }

    public static class LlmProperties {

        private final ClientConfig query = new ClientConfig("https://api.deepseek.com", "deepseek-chat", 0.7);
        private final ClientConfig media = new ClientConfig("https://generativelanguage.googleapis.com", "/v1beta/openai/chat/completions", "gemini-2.5-pro", 0.7);
        private final ClientConfig insight = new ClientConfig("https://api.moonshot.cn", "kimi-k2", 0.7);
        private final ClientConfig report = new ClientConfig("https://generativelanguage.googleapis.com", "/v1beta/openai/chat/completions", "gemini-2.5-pro", 0.7);
        private final ClientConfig forumHost = new ClientConfig("https://api.siliconflow.cn", "Qwen/Qwen3-235B-A22B", 0.6);
        private final ClientConfig keywordOptimizer = new ClientConfig("https://api.siliconflow.cn", "Qwen/Qwen3-235B-A22B", 0.7);
        private final ClientConfig mindspider = new ClientConfig("https://api.deepseek.com", "deepseek-chat", 0.7);

        public ClientConfig getQuery() {
            return query;
        }

        public ClientConfig getMedia() {
            return media;
        }

        public ClientConfig getInsight() {
            return insight;
        }

        public ClientConfig getReport() {
            return report;
        }

        public ClientConfig getForumHost() {
            return forumHost;
        }

        public ClientConfig getKeywordOptimizer() {
            return keywordOptimizer;
        }

        public ClientConfig getMindspider() {
            return mindspider;
        }
    }

    public static class ClientConfig {

        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String completionsPath = "/v1/chat/completions";
        private String modelName;
        private double temperature;

        public ClientConfig() {
        }

        public ClientConfig(String baseUrl, String modelName, double temperature) {
            this.baseUrl = baseUrl;
            this.modelName = modelName;
            this.temperature = temperature;
        }

        public ClientConfig(String baseUrl, String completionsPath, String modelName, double temperature) {
            this.baseUrl = baseUrl;
            this.completionsPath = completionsPath;
            this.modelName = modelName;
            this.temperature = temperature;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCompletionsPath() {
            return completionsPath;
        }

        public void setCompletionsPath(String completionsPath) {
            this.completionsPath = completionsPath;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    public static class TavilyConfig {

        private String baseUrl = "https://api.tavily.com";
        private String apiKey = "";
        private int maxResults = 7;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }

    public static class BochaConfig {

        private String baseUrl = "https://api.bocha.example";
        private String apiKey = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class DatabaseSearchConfig {

        private String datasource = "postgresql";
        private String schema = "public";
        private String defaultPlatform = "weibo";

        public String getDatasource() {
            return datasource;
        }

        public void setDatasource(String datasource) {
            this.datasource = datasource;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getDefaultPlatform() {
            return defaultPlatform;
        }

        public void setDefaultPlatform(String defaultPlatform) {
            this.defaultPlatform = defaultPlatform;
        }
    }

    public static class SentimentMcpConfig {

        private boolean enabled = true;
        private String baseUrl = "http://localhost:8081";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
