package com.bettafish.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ApplicationYamlTest {

    @Test
    void usesSafeBooleanDefaultsAndNoEmbeddedSecrets() throws IOException {
        String applicationYml = readApplicationYaml();

        assertThat(applicationYml)
            .contains("enabled: ${BETTAFISH_LLM_QUERY_ENABLED:false}")
            .contains("enabled: ${BETTAFISH_LLM_MINDSPIDER_ENABLED:false}")
            .contains("api-key: ${BETTAFISH_TAVILY_API_KEY:}");
        assertThat(applicationYml)
            .doesNotContain("sk-")
            .doesNotContain("tvly-");
    }

    private String readApplicationYaml() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/application.yml")) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
