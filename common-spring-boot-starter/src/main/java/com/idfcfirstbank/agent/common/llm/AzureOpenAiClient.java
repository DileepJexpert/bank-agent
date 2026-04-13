package com.idfcfirstbank.agent.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM client for Azure OpenAI Service.
 * Activate with: llm.azure.enabled=true
 * Required: llm.azure.endpoint, llm.azure.api-key, llm.azure.deployment
 * Multiple providers can be enabled simultaneously for LlmRouter failover.
 */
@Component
@ConditionalOnProperty(name = "llm.azure.enabled", havingValue = "true")
public class AzureOpenAiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String deploymentName;
    private final String apiVersion;

    public AzureOpenAiClient(
            @Value("${llm.azure.endpoint:}") String endpoint,
            @Value("${llm.azure.api-key:}") String apiKey,
            @Value("${llm.azure.deployment:gpt-4o}") String deploymentName,
            @Value("${llm.azure.api-version:2024-02-15-preview}") String apiVersion,
            @Value("${llm.timeout:30s}") Duration timeout) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException(
                    "llm.azure.endpoint must be set when llm.azure.enabled=true");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "llm.azure.api-key must be set when llm.azure.enabled=true");
        }
        this.restClient = RestClient.builder()
                .baseUrl(endpoint)
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(timeout)
                                .withReadTimeout(timeout)))
                .defaultHeader("api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.deploymentName = deploymentName;
        this.apiVersion = apiVersion;
        log.info("LLM Provider: Azure OpenAI, deployment: {}, timeout: {}", deploymentName, timeout);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> body = Map.of(
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.1
            );

            String response = restClient.post()
                    .uri("/openai/deployments/{d}/chat/completions?api-version={v}",
                            deploymentName, apiVersion)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("Azure OpenAI returned no completion content: " + response);
            }
            return content.asText();

        } catch (Exception e) {
            log.error("Azure OpenAI chat failed: {}", e.getMessage());
            throw new RuntimeException("Azure OpenAI call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String chat(String systemPrompt, String userMessage, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return chat(systemPrompt, userMessage);
        }
        throw new UnsupportedOperationException(
                "AzureOpenAiClient does not support tool-calling yet. "
                + "Use llm.azure.enabled=false and llm.openai.enabled=true for tool-calling support.");
    }

    @Override
    public String getProvider() {
        return "azure:" + deploymentName;
    }
}
