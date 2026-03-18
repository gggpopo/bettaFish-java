package com.bettafish.insight.keyword;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KeywordOptimizer {

    public List<String> optimize(String query) {
        return List.of(query, query + " 评论", query + " 热度");
    }
}
