package com.idfcfirstbank.agent.vault.identity.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerVerifyRequest(
        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotBlank(message = "OTP is required")
        String otp,

        @NotBlank(message = "Channel is required")
        String channel
) {
}
