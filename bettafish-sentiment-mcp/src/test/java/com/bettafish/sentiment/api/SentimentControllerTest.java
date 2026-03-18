package com.bettafish.sentiment.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.bettafish.sentiment.OnnxSentimentAnalyzer;

class SentimentControllerTest {

    @Test
    void classifiesTextUsingStubAnalyzer() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SentimentController(new OnnxSentimentAnalyzer()))
            .build();

        mockMvc.perform(post("/api/sentiment/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"这个活动太棒了，大家都很喜欢"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("POSITIVE"))
            .andExpect(jsonPath("$.confidence").value(0.85));
    }
}
