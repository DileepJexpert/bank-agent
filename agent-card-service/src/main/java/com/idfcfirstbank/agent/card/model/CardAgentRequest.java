package com.idfcfirstbank.agent.card.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Inbound request for card-related queries, typically routed from the orchestrator.
 *
 * @param sessionId           the conversation session identifier
 * @param customerId          the authenticated customer identifier
 * @param intent              the detected intent (CARD_BLOCK, REWARD_POINTS, DISPUTE_RAISE, etc.)
 * @param message             the customer's message text
 * @param conversationHistory previous conversation turns for context
 * @param language            preferred language for the response (e.g. "en", "hi")
 * @param customerContext     additional context (e.g. cardLast4, transactionId, amount)
 */
public record CardAgentRequest(
        String sessionId,
        @NotBlank(message = "customerId is required") String customerId,
        String intent,
        @NotBlank(message = "message is required") String message,
        List<String> conversationHistory,
        String language,
        Map<String, String> customerContext
) {
    public CardAgentRequest {
        if (conversationHistory == null) conversationHistory = List.of();
        if (customerContext == null) customerContext = Map.of();
        if (sessionId == null) sessionId = "";
        if (language == null) language = "en";
    }
}
