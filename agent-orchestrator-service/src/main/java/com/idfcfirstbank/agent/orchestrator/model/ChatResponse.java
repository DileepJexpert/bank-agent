package com.idfcfirstbank.agent.orchestrator.model;

/**
 * Outbound chat response returned to the customer.
 *
 * @param sessionId  the conversation session identifier
 * @param message    the response message text
 * @param agentType  which domain agent handled the query (e.g. ACCOUNT, LOANS)
 * @param intent     the detected customer intent
 * @param confidence confidence score of the intent detection (0.0 - 1.0)
 * @param escalated  whether the query was escalated to a human agent
 */
public record ChatResponse(
        String sessionId,
        String message,
        String agentType,
        String intent,
        double confidence,
        boolean escalated
) {
}
