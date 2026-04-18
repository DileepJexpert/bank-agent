package com.idfcfirstbank.agent.collections.config;

import com.idfcfirstbank.agent.common.llm.LlmRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Slf4j
@Configuration
public class CollectionsAgentConfig {

    @Bean
    public String collectionsLlmInfo(LlmRouter llmRouter) {
        log.info("Collections Agent using LLM provider: {}", llmRouter.getActiveProvider());
        return llmRouter.getActiveProvider();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
