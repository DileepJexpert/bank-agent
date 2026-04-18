package com.idfcfirstbank.agent.common.llm;

import java.util.List;

/**
 * Common interface for all LLM provider clients.
 * Implementations: OllamaClient, AnthropicClient, OpenAiClient, AzureOpenAiClient.
 * Switch provider via llm.provider in application.yml — zero code change needed.
 */
public interface LlmClient {
    String chat(String systemPrompt, String userMessage);
    String chat(String systemPrompt, String userMessage, List<ToolDefinition> tools);
    String getProvider();
}
