package com.bettafish.forum;

import org.springframework.stereotype.Service;
import com.bettafish.forum.prompt.ForumPrompts;

@Service
public class ForumHost {

    public String moderate(String query, int viewpoints) {
        return ForumPrompts.HOST_SYSTEM_PROMPT + " " + viewpoints + " engine viewpoints for " + query + ".";
    }
}
