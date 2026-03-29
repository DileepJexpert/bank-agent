package com.idfcfirstbank.agent.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for multi-LLM provider support.
 * Bind via application.yml under the {@code agent.llm} prefix.
 *
 * <pre>
 * agent:
 *   llm:
 *     provider: anthropic
 *     model-name: claude-sonnet-4-20250514
 *     api-key: ${LLM_API_KEY}
 *     base-url: https://api.anthropic.com
 *     temperature: 0.7
 *     max-tokens: 4096
 *     timeout: 30s
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "agent.llm")
public class LlmProviderConfig {

    /**
     * LLM provider to use. Supported values: anthropic, openai, ollama, azure-openai, mistral.
     */
    private String provider = "anthropic";

    /**
     * Model name / identifier for the chosen provider.
     */
    private String modelName = "claude-sonnet-4-20250514";

    /**
     * API key used to authenticate with the LLM provider.
     */
    private String apiKey;

    /**
     * Base URL for the LLM provider API endpoint.
     */
    private String baseUrl;

    /**
     * Sampling temperature (0.0 – 1.0). Lower values produce more deterministic output.
     */
    private double temperature = 0.7;

    /**
     * Maximum number of tokens the model may generate in a single response.
     */
    private int maxTokens = 4096;

    /**
     * HTTP request timeout when calling the LLM provider.
     */
    private Duration timeout = Duration.ofSeconds(30);
}
