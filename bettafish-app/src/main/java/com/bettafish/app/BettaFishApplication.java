package com.bettafish.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.bettafish")
@ConfigurationPropertiesScan(basePackages = "com.bettafish.common.config")
public class BettaFishApplication {

    public static void main(String[] args) {
        SpringApplication.run(BettaFishApplication.class, args);
    }
}
