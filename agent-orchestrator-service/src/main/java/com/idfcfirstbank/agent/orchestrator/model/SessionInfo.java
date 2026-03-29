package com.idfcfirstbank.agent.orchestrator.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Session metadata stored in Redis for conversation tracking.
 *
 * @param sessionId  unique session identifier
 * @param customerId authenticated customer identifier
 * @param channel    originating channel
 * @param language   preferred language
 * @param state      current conversation state
 * @param history    ordered list of conversation messages
 * @param createdAt  when the session was created
 */
public record SessionInfo(
        String sessionId,
        String customerId,
        String channel,
        String language,
        String state,
        List<MessageEntry> history,
        Instant createdAt
) implements Serializable {

    /**
     * A single message in the conversation history.
     *
     * @param role      who sent the message (customer, assistant, system)
     * @param content   the message text
     * @param timestamp when the message was sent
     */
    public record MessageEntry(
            String role,
            String content,
            Instant timestamp
    ) implements Serializable {
    }
}
