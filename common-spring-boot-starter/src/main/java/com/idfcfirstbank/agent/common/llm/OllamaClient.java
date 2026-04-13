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
 * LLM client for Ollama (local, free, no API key needed).
 * Default provider if llm.provider is not set.
 * Supports llama3.1, mistral, gemma2, phi3, and any other Ollama model.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String model;

    public OllamaClient(
            @Value("${llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${llm.ollama.model:llama3.1}") String model) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = model;
        log.info("LLM Provider: Ollama @ {}, model: {}", baseUrl, model);
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
                    "stream", false,
                    "options", Map.of("temperature", 0.1, "num_predict", 1024)
            );

            String response = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response);
            return root.path("message").path("content").asText();

        } catch (Exception e) {
            log.error("Ollama chat failed: {}", e.getMessage());
            throw new RuntimeException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String chat(String systemPrompt, String userMessage, List<ToolDefinition> tools) {
        // Build tool descriptions into system prompt (Ollama native tool support is model-dependent)
        StringBuilder augmented = new StringBuilder(systemPrompt);
        augmented.append("\n\nAvailable tools (call by responding with JSON only):\n");
        for (ToolDefinition tool : tools) {
            augmented.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        augmented.append("\nIf a tool call is needed, respond ONLY with: "
                + "{\"tool\": \"<name>\", \"parameters\": {<key>: <value>}}");
        return chat(augmented.toString(), userMessage);
    }

    @Override
    public String getProvider() {
        return "ollama:" + model;
    }
}
