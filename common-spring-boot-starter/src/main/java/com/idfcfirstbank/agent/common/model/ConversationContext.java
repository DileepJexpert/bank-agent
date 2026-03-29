package com.idfcfirstbank.agent.common.model;

import java.time.Instant;
import java.util.Map;

/**
 * Shared conversation context carried across agent boundaries during a customer interaction.
 *
 * @param sessionId    unique identifier for the conversation session
 * @param customerId   identifier of the customer (may be masked in logs)
 * @param channel      originating channel (e.g. "mobile", "web", "whatsapp", "branch")
 * @param language     preferred language code (e.g. "en", "hi")
 * @param currentIntent the currently recognised customer intent
 * @param agentType    the agent type that is currently handling the conversation
 * @param metadata     arbitrary key-value pairs for extensibility
 * @param timestamp    when this context snapshot was created / last updated
 */
public record ConversationContext(
        String sessionId,
        String customerId,
        String channel,
        String language,
        String currentIntent,
        AgentType agentType,
        Map<String, Object> metadata,
        Instant timestamp
) {
}
