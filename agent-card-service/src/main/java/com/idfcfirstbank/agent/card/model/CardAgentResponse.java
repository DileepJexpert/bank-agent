package com.idfcfirstbank.agent.card.model;

import java.util.List;
import java.util.Map;

/**
 * Response from the Card Agent for a processed query.
 *
 * @param sessionId    the conversation session identifier
 * @param response     the response message text (card numbers always masked)
 * @param data         structured data (e.g. cardLast4, blockReference, disputeId)
 * @param mcpCallsMade list of MCP tool endpoints that were called
 * @param tier         the processing tier used (TIER_0, TIER_1, TIER_2, GENERAL)
 */
public record CardAgentResponse(
        String sessionId,
        String response,
        Map<String, String> data,
        List<String> mcpCallsMade,
        String tier
) {
    public CardAgentResponse {
        if (data == null) data = Map.of();
        if (mcpCallsMade == null) mcpCallsMade = List.of();
        if (tier == null) tier = "GENERAL";
    }

    /**
     * Create a response for a vault-denied request.
     */
    public static CardAgentResponse denied(String sessionId, String reason, String intent) {
        return new CardAgentResponse(
                sessionId,
                "I'm unable to process this request at the moment. " + reason,
                Map.of(),
                List.of(),
                intent != null ? intent : "DENIED"
        );
    }

    /**
     * Create a response for a request that requires escalation.
     */
    public static CardAgentResponse escalated(String sessionId, String intent) {
        return new CardAgentResponse(
                sessionId,
                "This request requires additional verification. Connecting you to a specialist.",
                Map.of(),
                List.of(),
                intent != null ? intent : "ESCALATED"
        );
    }
}
