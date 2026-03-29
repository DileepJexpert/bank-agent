package com.idfcfirstbank.agent.vault.policy.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record PolicyEvaluationRequest(
        @NotBlank(message = "Agent ID is required")
        String agentId,

        @NotBlank(message = "Agent type is required")
        String agentType,

        @NotBlank(message = "Action is required")
        String action,

        @NotBlank(message = "Resource is required")
        String resource,

        String customerId,

        Map<String, Object> context,

        @NotNull(message = "Timestamp is required")
        Instant timestamp
) {
}
