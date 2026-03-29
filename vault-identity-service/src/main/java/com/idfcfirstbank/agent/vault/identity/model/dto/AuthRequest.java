package com.idfcfirstbank.agent.vault.identity.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank(message = "Service ID is required")
        String serviceId,

        @NotBlank(message = "API key is required")
        String apiKey,

        @NotBlank(message = "Image hash is required")
        String imageHash
) {
}
