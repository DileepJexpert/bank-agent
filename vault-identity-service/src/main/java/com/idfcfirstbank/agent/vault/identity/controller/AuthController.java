package com.idfcfirstbank.agent.vault.identity.controller;

import com.idfcfirstbank.agent.vault.identity.model.dto.AuthRequest;
import com.idfcfirstbank.agent.vault.identity.model.dto.AuthResponse;
import com.idfcfirstbank.agent.vault.identity.model.dto.CustomerVerifyRequest;
import com.idfcfirstbank.agent.vault.identity.model.dto.TokenValidationResponse;
import com.idfcfirstbank.agent.vault.identity.service.AgentAuthService;
import com.idfcfirstbank.agent.vault.identity.service.CustomerAuthService;
import com.idfcfirstbank.agent.vault.identity.service.JwksService;
import com.idfcfirstbank.agent.vault.identity.service.TokenService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Vault identity and authentication endpoints")
public class AuthController {

    private final AgentAuthService agentAuthService;
    private final CustomerAuthService customerAuthService;
    private final TokenService tokenService;
    private final JwksService jwksService;

    @PostMapping("/agent/authenticate")
    @Operation(summary = "Authenticate an agent instance",
            description = "Service-to-service authentication using API key and container image hash")
    public ResponseEntity<AuthResponse> authenticateAgent(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = agentAuthService.authenticate(request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.warn("Agent authentication failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/customer/verify")
    @Operation(summary = "Verify customer identity via OTP",
            description = "Verifies customer using OTP and issues a session token")
    public ResponseEntity<AuthResponse> verifyCustomer(@Valid @RequestBody CustomerVerifyRequest request) {
        try {
            AuthResponse response = customerAuthService.verifyCustomer(request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.warn("Customer verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "Refresh an existing token",
            description = "Issues a new token with extended expiry for a valid existing token")
    public ResponseEntity<Map<String, String>> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.badRequest().build();
        }

        String newToken = tokenService.refreshToken(token);
        if (newToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(Map.of("token", newToken));
    }

    @PostMapping("/token/validate")
    @Operation(summary = "Validate a token",
            description = "Validates a token and returns its claims including agent type and scopes")
    @SuppressWarnings("unchecked")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.badRequest().build();
        }

        Claims claims = tokenService.validateToken(token);
        if (claims == null) {
            return ResponseEntity.ok(new TokenValidationResponse(false, null, List.of(), null));
        }

        String tokenType = claims.get("type", String.class);
        String agentType = "agent".equals(tokenType) ? claims.get("agent_type", String.class) : null;
        List<String> scopes = claims.get("scopes", List.class);
        String customerId = "customer".equals(tokenType) ? claims.getSubject() : null;

        return ResponseEntity.ok(new TokenValidationResponse(
                true,
                agentType,
                scopes != null ? scopes : List.of(),
                customerId
        ));
    }

    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get JSON Web Key Set",
            description = "Returns the public keys used to verify JWTs issued by this service, in JWKS format (RFC 7517)")
    public ResponseEntity<Map<String, Object>> getJwks() {
        Map<String, Object> jwks = jwksService.getJwks();
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")
                .body(jwks);
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
