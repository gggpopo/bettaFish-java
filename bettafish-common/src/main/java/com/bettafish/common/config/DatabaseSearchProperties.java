package com.bettafish.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bettafish.database-search")
public class DatabaseSearchProperties {

    private boolean enabled = false;
    private String datasource = "postgresql";
    private String schema = "public";
    private String defaultPlatform = "weibo";
    private int defaultLimitPerTable = 50;
    private int maxContentLength = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public int getDefaultLimitPerTable() {
        return defaultLimitPerTable;
    }

    public void setDefaultLimitPerTable(int defaultLimitPerTable) {
        this.defaultLimitPerTable = defaultLimitPerTable;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
}
