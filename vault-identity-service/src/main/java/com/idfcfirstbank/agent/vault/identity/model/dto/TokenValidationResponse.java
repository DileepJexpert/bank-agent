package com.idfcfirstbank.agent.vault.identity.model.dto;

import java.util.List;

public record TokenValidationResponse(
        boolean valid,
        String agentType,
        List<String> scopes,
        String customerId
) {
}
