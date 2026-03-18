package com.bettafish.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bettafish.database-search")
public class DatabaseSearchProperties {

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
