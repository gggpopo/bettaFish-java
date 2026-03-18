package com.bettafish.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.bettafish.common.config.SentimentMcpProperties;

class HttpSentimentAnalysisClientTest {

    @Test
    void postsTextToSentimentServiceAndReturnsParsedSignal() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();

        server.expect(requestTo("http://localhost:8081/api/sentiment/analyze"))
            .andExpect(method(POST))
            .andExpect(content().json("""
                {"text":"武汉大学樱花太棒了"}
                """))
            .andRespond(withSuccess("""
                {"label":"POSITIVE","confidence":0.85}
                """, MediaType.APPLICATION_JSON));

        SentimentMcpProperties properties = new SentimentMcpProperties();
        HttpSentimentAnalysisClient client = new HttpSentimentAnalysisClient(restClientBuilder, properties);

        SentimentSignal signal = client.analyze("武汉大学樱花太棒了");

        assertEquals("POSITIVE", signal.label());
        assertEquals(0.85, signal.confidence());
        assertTrue(signal.enabled());
        server.verify();
    }

    @Test
    void returnsDisabledSignalWhenSentimentServiceIsTurnedOff() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        SentimentMcpProperties properties = new SentimentMcpProperties();
        properties.setEnabled(false);
        HttpSentimentAnalysisClient client = new HttpSentimentAnalysisClient(restClientBuilder, properties);

        SentimentSignal signal = client.analyze("任意文本");

        assertEquals("DISABLED", signal.label());
        assertEquals(0.0, signal.confidence());
        assertFalse(signal.enabled());
    }
}
