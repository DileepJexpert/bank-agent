package com.idfcfirstbank.agent.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LLM client for OpenAI GPT models (also works for Mistral via base-url override).
 * Activate with: llm.provider=openai and llm.openai.api-key=sk-...
 * For Mistral: llm.provider=openai, llm.openai.base-url=https://api.mistral.ai/v1
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String model;

    public OpenAiClient(
            @Value("${llm.openai.api-key:}") String apiKey,
            @Value("${llm.openai.model:gpt-4o}") String model,
            @Value("${llm.openai.base-url:https://api.openai.com}") String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "llm.openai.api-key must be set when llm.provider=openai");
        }
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = model;
        log.info("LLM Provider: OpenAI @ {}, model: {}", baseUrl, model);
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.1
            );

            String response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("OpenAI returned no completion content: " + response);
            }
            return content.asText();

        } catch (Exception e) {
            log.error("OpenAI chat failed: {}", e.getMessage());
            throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String chat(String systemPrompt, String userMessage, List<ToolDefinition> tools) {
        try {
            List<Map<String, Object>> toolDefs = tools.stream()
                    .map(t -> Map.<String, Object>of(
                            "type", "function",
                            "function", Map.of(
                                    "name", t.name(),
                                    "description", t.description(),
                                    "parameters", Map.of("type", "object", "properties", t.parameters())
                            )
                    )).toList();

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "tools", toolDefs,
                    "temperature", 0.1
            );

            String response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response);
            JsonNode message = root.path("choices").get(0).path("message");

            if (message.has("tool_calls")) {
                JsonNode function = message.path("tool_calls").get(0).path("function");
                JsonNode arguments = MAPPER.readTree(function.path("arguments").asText("{}"));
                return MAPPER.writeValueAsString(Map.of(
                        "tool", function.path("name").asText(),
                        "parameters", arguments
                ));
            }
            return message.path("content").asText();

        } catch (org.springframework.web.client.RestClientException e) {
            log.warn("OpenAI tool call failed (HTTP error), retrying without tools: {}", e.getMessage());
            return chat(systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("OpenAI tool call failed", e);
            throw new RuntimeException("OpenAI tool call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProvider() {
        return "openai:" + model;
    }
}
