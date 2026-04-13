package com.idfcfirstbank.agent.orchestrator.config;

import com.idfcfirstbank.agent.common.llm.LlmRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM configuration for the orchestrator.
 * Provider is selected via llm.provider in application.yml.
 * No code change needed to switch providers — only YAML change.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Value("${llm.provider:ollama}")
    private String provider;

    /**
     * Log active provider at startup. LlmRouter is auto-configured via
     * common-spring-boot-starter based on llm.provider property.
     */
    @Bean
    public String llmProviderInfo(LlmRouter llmRouter) {
        String active = llmRouter.getActiveProvider();
        log.info("Orchestrator using LLM provider: {}", active);
        return active;
    }
}
