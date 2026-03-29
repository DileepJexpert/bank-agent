package com.idfcfirstbank.agent.gateway.config;

import com.idfcfirstbank.agent.gateway.filter.VaultPolicyGatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.time.Duration;

@Configuration
public class GatewayConfig {

    private static final String CIRCUIT_BREAKER_FALLBACK = "forward:/fallback";

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder,
                                           VaultPolicyGatewayFilter vaultPolicyFilter,
                                           RedisRateLimiter redisRateLimiter) {
        return builder.routes()

                // Agent Orchestrator Service
                .route("agent-orchestrator-service", r -> r
                        .path("/api/v1/orchestrator/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("orchestratorCircuitBreaker")
                                        .setFallbackUri(CIRCUIT_BREAKER_FALLBACK)
                                        .setStatusCodes(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                                                HttpStatus.SERVICE_UNAVAILABLE.name()))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setDenyEmptyKey(false))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(2), 2, true)))
                        .uri("lb://agent-orchestrator-service"))

                // Agent Account Service
                .route("agent-account-service", r -> r
                        .path("/api/v1/accounts/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("accountCircuitBreaker")
                                        .setFallbackUri(CIRCUIT_BREAKER_FALLBACK)
                                        .setStatusCodes(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                                                HttpStatus.SERVICE_UNAVAILABLE.name()))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setDenyEmptyKey(false))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(2), 2, true)))
                        .uri("lb://agent-account-service"))

                // Vault Identity Service
                .route("vault-identity-service", r -> r
                        .path("/api/v1/vault/identity/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("vaultIdentityCircuitBreaker")
                                        .setFallbackUri(CIRCUIT_BREAKER_FALLBACK)
                                        .setStatusCodes(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                                                HttpStatus.SERVICE_UNAVAILABLE.name()))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setDenyEmptyKey(false))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(1), 2, true)))
                        .uri("lb://vault-identity-service"))

                // Vault Policy Service
                .route("vault-policy-service", r -> r
                        .path("/api/v1/vault/policy/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("vaultPolicyCircuitBreaker")
                                        .setFallbackUri(CIRCUIT_BREAKER_FALLBACK)
                                        .setStatusCodes(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                                                HttpStatus.SERVICE_UNAVAILABLE.name()))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setDenyEmptyKey(false))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(1), 2, true)))
                        .uri("lb://vault-policy-service"))

                // Vault Audit Service
                .route("vault-audit-service", r -> r
                        .path("/api/v1/vault/audit/**")
                        .filters(f -> f
                                .circuitBreaker(cb -> cb
                                        .setName("vaultAuditCircuitBreaker")
                                        .setFallbackUri(CIRCUIT_BREAKER_FALLBACK)
                                        .setStatusCodes(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                                                HttpStatus.SERVICE_UNAVAILABLE.name()))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setDenyEmptyKey(false))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(2), 2, true)))
                        .uri("lb://vault-audit-service"))

                // MCP Core Banking Server (policy-enforced)
                .route("mcp-core-banking-server", r -> r
                        .path("/api/v1/mcp/core-banking/**")
                        .filters(f -> f
                                .filter(vaultPolicyFilter.apply(new VaultPolicyGatewayFilter.Config()))
                                .circuitBreaker(cb -> cb
                                        .setName("mcpCoreBankingCircuitBreaker")
                                        .setFallbackUri(CIRCUIT_BREAKER_FALLBACK)
                                        .setStatusCodes(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                                                HttpStatus.SERVICE_UNAVAILABLE.name()))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter)
                                        .setDenyEmptyKey(false))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setBackoff(Duration.ofMillis(200), Duration.ofSeconds(2), 2, true)))
                        .uri("lb://mcp-core-banking-server"))

                .build();
    }
}
