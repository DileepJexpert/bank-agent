package com.idfcfirstbank.agent.orchestrator.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound chat request from a customer.
 *
 * @param sessionId  existing session identifier (null for first message)
 * @param customerId authenticated customer identifier
 * @param message    the customer's message text
 * @param channel    originating channel (mobile, web, whatsapp, branch)
 * @param language   preferred language code (e.g. en, hi)
 */
public record ChatRequest(
        String sessionId,
        @NotBlank(message = "customerId is required") String customerId,
        @NotBlank(message = "message is required") String message,
        String channel,
        String language
) {
    public ChatRequest {
        if (channel == null || channel.isBlank()) channel = "web";
        if (language == null || language.isBlank()) language = "en";
    }
}
