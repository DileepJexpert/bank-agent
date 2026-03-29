package com.idfcfirstbank.agent.vault.identity.model.dto;

import java.util.List;

public record AuthResponse(
        String token,
        long expiresIn,
        List<String> scopes
) {
}
