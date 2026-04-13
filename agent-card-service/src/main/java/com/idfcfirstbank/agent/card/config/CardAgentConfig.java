package com.idfcfirstbank.agent.card.config;

import com.idfcfirstbank.agent.common.llm.LlmRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the Card Agent Service.
 * LLM provider is selected via llm.provider in application.yml.
 * Switch provider (ollama/anthropic/openai/azure) with zero code change.
 */
@Slf4j
@Configuration
public class CardAgentConfig {

    @Bean
    public String cardAgentLlmInfo(LlmRouter llmRouter) {
        log.info("Card Agent using LLM provider: {}", llmRouter.getActiveProvider());
        return llmRouter.getActiveProvider();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** PCI-DSS: restrict Redis cache TTL to 1 minute for card data. */
    @Bean
    @Profile("pci-dss")
    public RedisCacheConfiguration pciDssRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(1));
    }
}
