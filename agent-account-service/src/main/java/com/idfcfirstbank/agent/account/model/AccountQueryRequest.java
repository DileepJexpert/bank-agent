package com.idfcfirstbank.agent.account.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Inbound request for account-related queries, typically routed from the orchestrator.
 *
 * @param sessionId  the conversation session identifier
 * @param customerId the authenticated customer identifier
 * @param message    the customer's message text
 * @param intent     the detected intent from the orchestrator
 * @param confidence the intent detection confidence score
 * @param parameters extracted parameters (e.g. account number, amount)
 */
public record AccountQueryRequest(
        String sessionId,
        @NotBlank(message = "customerId is required") String customerId,
        @NotBlank(message = "message is required") String message,
        String intent,
        double confidence,
        Map<String, String> parameters
) {
    public AccountQueryRequest {
        if (parameters == null) parameters = Map.of();
        if (sessionId == null) sessionId = "";
    }
}
