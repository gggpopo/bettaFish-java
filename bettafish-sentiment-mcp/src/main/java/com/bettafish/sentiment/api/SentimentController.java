package com.bettafish.sentiment.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bettafish.sentiment.OnnxSentimentAnalyzer;

@RestController
@RequestMapping("/api/sentiment")
public class SentimentController {

    private final OnnxSentimentAnalyzer analyzer;

    public SentimentController(OnnxSentimentAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @PostMapping("/analyze")
    public ResponseEntity<SentimentAnalysisResponse> analyze(@RequestBody SentimentAnalysisRequest request) {
        return ResponseEntity.ok(analyzer.analyze(request.text()));
    }
}
