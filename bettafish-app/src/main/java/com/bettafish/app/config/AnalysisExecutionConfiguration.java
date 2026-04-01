package com.bettafish.app.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalysisExecutionConfiguration {

    @Bean(name = "analysisExecutor", destroyMethod = "shutdown")
    public Executor analysisExecutor() {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        return Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "analysis-executor");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService analysisTimeoutScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "analysis-timeout-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean("analysisTaskTimeout")
    public Duration analysisTaskTimeout() {
        return Duration.ofMinutes(5);
    }
}
