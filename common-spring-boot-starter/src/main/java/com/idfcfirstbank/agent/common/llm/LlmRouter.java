package com.idfcfirstbank.agent.common.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Routes LLM calls to available providers with automatic failover.
 * <p>
 * Priority: whichever provider bean is active (set via llm.provider).
 * If the primary provider fails, falls back to the next available provider.
 * If ALL providers fail, returns a safe fallback message instead of throwing.
 * <p>
 * Inject this into any service that needs LLM access — never inject LlmClient directly.
 */
@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private static final String FALLBACK_MESSAGE =
            "I'm unable to process your request right now. Please try again in a moment.";

    private final List<LlmClient> clients;

    public LlmRouter(List<LlmClient> clients) {
        this.clients = clients;
        if (clients.isEmpty()) {
            log.warn("LlmRouter: no LLM providers found. All chat calls will return fallback message.");
        } else {
            log.info("LlmRouter initialised with {} provider(s): {}",
                    clients.size(),
                    clients.stream().map(LlmClient::getProvider).toList());
        }
    }

    /**
     * Send a chat message with automatic failover across all configured providers.
     */
    public String chat(String systemPrompt, String userMessage) {
        for (LlmClient client : clients) {
            try {
                String response = client.chat(systemPrompt, userMessage);
                if (response != null && !response.isBlank()) {
                    log.debug("LLM response from provider: {}", client.getProvider());
                    return response;
                }
            } catch (Exception e) {
                log.warn("LLM provider {} failed, trying next: {}", client.getProvider(), e.getMessage());
            }
        }
        log.error("All LLM providers failed for chat request");
        return FALLBACK_MESSAGE;
    }

    /**
     * Send a chat message with tool definitions, with automatic failover.
     */
    public String chatWithTools(String systemPrompt, String userMessage, List<ToolDefinition> tools) {
        for (LlmClient client : clients) {
            try {
                String response = client.chat(systemPrompt, userMessage, tools);
                if (response != null && !response.isBlank()) {
                    log.debug("LLM tool-call response from provider: {}", client.getProvider());
                    return response;
                }
            } catch (Exception e) {
                log.warn("LLM provider {} failed for tool call, trying next: {}",
                        client.getProvider(), e.getMessage());
            }
        }
        // Last resort: plain chat without tools
        return chat(systemPrompt, userMessage);
    }

    /**
     * Returns the name of the active (primary) provider, or "none" if none configured.
     */
    public String getActiveProvider() {
        return clients.isEmpty() ? "none" : clients.get(0).getProvider();
    }
}
