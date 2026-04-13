package com.idfcfirstbank.agent.internalops.config;

import com.idfcfirstbank.agent.common.llm.LlmRouter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class InternalOpsConfig {

    private final LlmRouter llmRouter;

    @PostConstruct
    void logLlmProvider() {
        log.info("Internal Ops Agent using LLM provider: {}", llmRouter.getActiveProvider());
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
