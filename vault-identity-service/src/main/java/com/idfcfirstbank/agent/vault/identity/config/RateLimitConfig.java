package com.idfcfirstbank.agent.vault.identity.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Redis-backed rate limiter for customer verification attempts.
 * Tracks verification attempts per customer using a sliding window counter.
 * Throws TooManyRequestsException (HTTP 429) when the limit is exceeded.
 */
@Component
@Slf4j
public class RateLimitConfig {

    private static final String RATE_LIMIT_KEY_PREFIX = "auth:rate:";

    private final StringRedisTemplate redisTemplate;
    private final int maxAttemptsPerMinute;
    private final Duration windowDuration;

    public RateLimitConfig(
            StringRedisTemplate redisTemplate,
            @Value("${vault.auth.rate-limit.max-attempts:10}") int maxAttemptsPerMinute,
            @Value("${vault.auth.rate-limit.window-seconds:60}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxAttemptsPerMinute = maxAttemptsPerMinute;
        this.windowDuration = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Checks whether the customer has exceeded the verification attempt rate limit.
     * If the limit is exceeded, throws a ResponseStatusException with HTTP 429.
     *
     * @param customerId the customer identifier to check
     * @throws ResponseStatusException if rate limit is exceeded
     */
    public void checkRateLimit(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return;
        }

        String key = RATE_LIMIT_KEY_PREFIX + customerId;

        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);
            if (currentCount == null) {
                log.warn("Redis increment returned null for key={}, allowing request", key);
                return;
            }

            // Set TTL only on first increment (when count transitions from 0 to 1)
            if (currentCount == 1L) {
                redisTemplate.expire(key, windowDuration);
            }

            if (currentCount > maxAttemptsPerMinute) {
                log.warn("Rate limit exceeded for customerId={}: {} attempts in window (max={})",
                        maskCustomerId(customerId), currentCount, maxAttemptsPerMinute);
                throw new TooManyRequestsException(
                        "Too many verification attempts. Please try again after "
                                + windowDuration.getSeconds() + " seconds.");
            }

            log.debug("Rate limit check passed for customerId={}: {}/{} attempts",
                    maskCustomerId(customerId), currentCount, maxAttemptsPerMinute);

        } catch (TooManyRequestsException e) {
            throw e;
        } catch (Exception e) {
            // Fail-open: if Redis is unavailable, allow the request but log the error
            log.error("Rate limit check failed due to Redis error for customerId={}: {}",
                    maskCustomerId(customerId), e.getMessage());
        }
    }

    /**
     * Returns the remaining attempts for a given customer within the current window.
     *
     * @param customerId the customer identifier
     * @return remaining attempts, or maxAttemptsPerMinute if no attempts recorded
     */
    public int getRemainingAttempts(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return maxAttemptsPerMinute;
        }

        String key = RATE_LIMIT_KEY_PREFIX + customerId;
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return maxAttemptsPerMinute;
            }
            int used = Integer.parseInt(value);
            return Math.max(0, maxAttemptsPerMinute - used);
        } catch (Exception e) {
            log.warn("Failed to get remaining attempts for customerId={}: {}",
                    maskCustomerId(customerId), e.getMessage());
            return maxAttemptsPerMinute;
        }
    }

    private String maskCustomerId(String customerId) {
        if (customerId.length() <= 4) {
            return "****";
        }
        return customerId.substring(0, 2) + "****" + customerId.substring(customerId.length() - 2);
    }

    /**
     * Exception thrown when a customer exceeds the rate limit for verification attempts.
     */
    public static class TooManyRequestsException extends ResponseStatusException {
        public TooManyRequestsException(String reason) {
            super(HttpStatus.TOO_MANY_REQUESTS, reason);
        }
    }
}
