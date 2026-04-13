package com.idfcfirstbank.agent.common.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Routes LLM calls to available providers with automatic failover.
 * <p>
 * Primary provider selection: if {@code llm.provider} is set, the matching
 * enabled client is placed first; any other enabled clients follow as
 * fallbacks. If {@code llm.provider} is unset or does not match any enabled
 * client, the discovery order of Spring beans is preserved.
 * <p>
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

    public LlmRouter(List<LlmClient> clients,
                     @Value("${llm.provider:}") String primaryProvider) {
        this.clients = orderByPrimary(clients, primaryProvider);
        if (this.clients.isEmpty()) {
            log.warn("LlmRouter: no LLM providers found. All chat calls will return fallback message.");
        } else {
            log.info("LlmRouter initialised with {} provider(s) (primary={}): {}",
                    this.clients.size(),
                    this.clients.get(0).getProvider(),
                    this.clients.stream().map(LlmClient::getProvider).toList());
        }
    }

    /**
     * Sort clients so that the one matching {@code primaryProvider} (by prefix
     * on {@link LlmClient#getProvider()}, e.g. "ollama" matches "ollama:llama3.1")
     * is first. All other enabled clients retain their relative order as
     * fallbacks.
     */
    private static List<LlmClient> orderByPrimary(List<LlmClient> clients, String primaryProvider) {
        List<LlmClient> ordered = new ArrayList<>(clients);
        if (primaryProvider == null || primaryProvider.isBlank()) {
            return ordered;
        }
        String needle = primaryProvider.toLowerCase().trim();
        ordered.sort(Comparator.comparingInt(c -> {
            String name = c.getProvider() == null ? "" : c.getProvider().toLowerCase();
            return (name.equals(needle) || name.startsWith(needle + ":")) ? 0 : 1;
        }));
        return ordered;
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
