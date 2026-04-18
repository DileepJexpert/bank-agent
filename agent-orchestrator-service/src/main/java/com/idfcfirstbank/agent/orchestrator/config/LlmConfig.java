package com.idfcfirstbank.agent.orchestrator.config;

import com.idfcfirstbank.agent.common.llm.LlmRouter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * LLM configuration for the orchestrator.
 * Provider is selected via llm.provider in application.yml.
 * No code change needed to switch providers — only YAML change.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmConfig {

    private final LlmRouter llmRouter;

    @PostConstruct
    void logLlmProvider() {
        log.info("Orchestrator using LLM provider: {}", llmRouter.getActiveProvider());
    }
}
