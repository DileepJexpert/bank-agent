package com.idfcfirstbank.agent.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Redis-backed rate limiter configuration for the API Gateway.
 * <p>
 * Uses a sliding window token bucket algorithm backed by Redis to enforce
 * per-client request rate limits across all gateway instances.
 */
@Configuration
public class RateLimiterConfig {

    @Value("${gateway.rate-limiter.replenish-rate:50}")
    private int defaultReplenishRate;

    @Value("${gateway.rate-limiter.burst-capacity:100}")
    private int defaultBurstCapacity;

    @Value("${gateway.rate-limiter.requested-tokens:1}")
    private int defaultRequestedTokens;

    /**
     * Configures the Redis-based rate limiter with a token bucket algorithm.
     *
     * @return RedisRateLimiter with configured replenish rate and burst capacity
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(defaultReplenishRate, defaultBurstCapacity, defaultRequestedTokens);
    }

    /**
     * Resolves the rate limit key from the request. Uses the following precedence:
     * <ol>
     *   <li>X-Agent-Id header (for agent-to-agent calls)</li>
     *   <li>X-Client-Id header (for identified API consumers)</li>
     *   <li>Remote address (fallback for unauthenticated requests)</li>
     * </ol>
     *
     * @return KeyResolver for rate limiting
     */
    @Bean
    public KeyResolver keyResolver() {
        return exchange -> {
            var request = exchange.getRequest();

            // Prefer agent identity for rate limiting
            String agentId = request.getHeaders().getFirst("X-Agent-Id");
            if (agentId != null && !agentId.isBlank()) {
                return Mono.just("agent:" + agentId);
            }

            // Fall back to client identity
            String clientId = request.getHeaders().getFirst("X-Client-Id");
            if (clientId != null && !clientId.isBlank()) {
                return Mono.just("client:" + clientId);
            }

            // Last resort: remote address
            String remoteAddr = Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
            return Mono.just("ip:" + remoteAddr);
        };
    }
}
