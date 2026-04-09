package com.bettafish.app.config;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AnalysisExecutionConfiguration {

    @Bean(name = "analysisExecutor", destroyMethod = "shutdown")
    public ExecutorService analysisExecutor() {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        AtomicInteger threadIndex = new AtomicInteger();
        return Executors.newFixedThreadPool(poolSize, runnable -> namedThread("analysis-coordinator-", threadIndex, runnable));
    }

    @Bean(name = "analysisEngineExecutor", destroyMethod = "shutdown")
    public ExecutorService analysisEngineExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("analysis-engine-", 0).factory());
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService analysisTimeoutScheduler() {
        AtomicInteger threadIndex = new AtomicInteger();
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            return namedThread("analysis-timeout-scheduler-", threadIndex, runnable);
        });
    }

    @Bean
    public AnalysisExecutionPolicy analysisExecutionPolicy(
        @Value("${bettafish.analysis.task-timeout:PT5M}") Duration taskTimeout,
        @Value("${bettafish.analysis.engine-timeout:PT90S}") Duration engineTimeout,
        @Value("${bettafish.analysis.max-concurrent-engines:3}") int maxConcurrentEngines
    ) {
        return new AnalysisExecutionPolicy(taskTimeout, engineTimeout, maxConcurrentEngines);
    }

    private Thread namedThread(String prefix, AtomicInteger threadIndex, Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + threadIndex.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
