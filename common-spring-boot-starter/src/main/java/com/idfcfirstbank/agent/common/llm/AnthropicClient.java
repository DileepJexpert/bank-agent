package com.idfcfirstbank.agent.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * LLM client for Anthropic Claude.
 * Activate with: llm.provider=anthropic and llm.anthropic.api-key=sk-ant-...
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic")
public class AnthropicClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String model;

    public AnthropicClient(
            @Value("${llm.anthropic.api-key:}") String apiKey,
            @Value("${llm.anthropic.model:claude-sonnet-4-20250514}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "llm.anthropic.api-key must be set when llm.provider=anthropic");
        }
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = model;
        log.info("LLM Provider: Anthropic, model: {}", model);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "system", systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", userMessage))
            );

            String response = restClient.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response);
            JsonNode contentArray = root.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                log.warn("Anthropic returned empty content array");
                return "";
            }
            return contentArray.get(0).path("text").asText();

        } catch (Exception e) {
            log.error("Anthropic chat failed", e);
            throw new RuntimeException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String chat(String systemPrompt, String userMessage, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return chat(systemPrompt, userMessage);
        }
        try {
            List<Map<String, Object>> toolDefs = tools.stream()
                    .map(t -> Map.<String, Object>of(
                            "name", t.name(),
                            "description", t.description(),
                            "input_schema", Map.of("type", "object", "properties", t.parameters())
                    )).toList();

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 1024,
                    "system", systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", userMessage)),
                    "tools", toolDefs
            );

            String response = restClient.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response);
            JsonNode content = root.path("content");
            StringBuilder result = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    result.append(block.path("text").asText());
                } else if ("tool_use".equals(block.path("type").asText())) {
                    result.append("{\"tool\":\"").append(block.path("name").asText())
                            .append("\",\"parameters\":").append(block.path("input").toString()).append("}");
                }
            }
            return result.toString();

        } catch (RestClientException e) {
            log.warn("Anthropic tool call failed (HTTP error), retrying without tools: {}", e.getMessage());
            return chat(systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("Anthropic tool call failed", e);
            throw new RuntimeException("Anthropic tool call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProvider() {
        return "anthropic:" + model;
    }
}
