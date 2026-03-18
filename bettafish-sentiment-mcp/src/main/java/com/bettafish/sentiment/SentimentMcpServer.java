package com.bettafish.sentiment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.bettafish.sentiment")
public class SentimentMcpServer {

    public static void main(String[] args) {
        SpringApplication.run(SentimentMcpServer.class, args);
    }
}
