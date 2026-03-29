package com.idfcfirstbank.agent.vault.identity.service;

import com.idfcfirstbank.agent.vault.identity.model.AgentCredentials;
import com.idfcfirstbank.agent.vault.identity.model.dto.AuthRequest;
import com.idfcfirstbank.agent.vault.identity.model.dto.AuthResponse;
import com.idfcfirstbank.agent.vault.identity.repository.AgentCredentialsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentAuthService {

    private final AgentCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Authenticates an agent instance using service credentials.
     * Validates the API key and container image hash against the approved baseline.
     * Issues a scoped JWT with agent type, allowed MCP servers, and data access level.
     *
     * @param request the authentication request containing serviceId, apiKey, and imageHash
     * @return AuthResponse with a scoped JWT token
     * @throws SecurityException if authentication fails
     */
    public AuthResponse authenticate(AuthRequest request) {
        log.info("Authenticating agent service: {}", request.serviceId());

        AgentCredentials credentials = credentialsRepository
                .findByAgentIdAndActiveTrue(request.serviceId())
                .orElseThrow(() -> {
                    log.warn("Unknown or inactive agent: {}", request.serviceId());
                    return new SecurityException("Invalid agent credentials");
                });

        // Validate API key
        if (!passwordEncoder.matches(request.apiKey(), credentials.getApiKeyHash())) {
            log.warn("API key mismatch for agent: {}", request.serviceId());
            throw new SecurityException("Invalid agent credentials");
        }

        // Validate container image hash against approved baseline
        if (!validateImageHash(request.imageHash(), credentials.getImageHash())) {
            log.error("Image hash mismatch for agent: {}. Expected: {}, Got: {}",
                    request.serviceId(), credentials.getImageHash(), request.imageHash());
            throw new SecurityException("Container image hash does not match approved baseline");
        }

        List<String> scopes = parseScopes(credentials.getAllowedScopes());
        List<String> mcpServers = parseMcpServers(credentials.getAllowedMcpServers());

        String token = tokenService.generateAgentToken(
                credentials.getAgentId(),
                credentials.getAgentType(),
                scopes,
                mcpServers,
                credentials.getDataAccessLevel()
        );

        log.info("Agent authenticated successfully: {} with scopes: {}", request.serviceId(), scopes);

        return new AuthResponse(token, 900L, scopes); // 15 min = 900 seconds
    }

    /**
     * Validates the presented container image hash against the registered approved baseline.
     */
    private boolean validateImageHash(String presentedHash, String approvedHash) {
        if (approvedHash == null || approvedHash.isBlank()) {
            log.debug("No image hash baseline configured, skipping validation");
            return true;
        }
        return approvedHash.equals(presentedHash);
    }

    private List<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> parseMcpServers(String mcpServers) {
        if (mcpServers == null || mcpServers.isBlank()) {
            return List.of();
        }
        return Arrays.stream(mcpServers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
