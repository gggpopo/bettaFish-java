package com.bettafish.app.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalysisExecutionConfiguration {

    @Bean
    public Executor analysisExecutor() {
        return Runnable::run;
    }
}
