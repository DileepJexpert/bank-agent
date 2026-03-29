package com.idfcfirstbank.agent.vault.identity.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TokenService {

    private static final long AGENT_TOKEN_TTL_MINUTES = 15;
    private static final long CUSTOMER_TOKEN_TTL_MINUTES = 30;
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    private final SecretKey signingKey;
    private final StringRedisTemplate redisTemplate;

    @Value("${vault.jwt.issuer:vault-identity-service}")
    private String issuer;

    public TokenService(@Value("${vault.jwt.secret}") String secret,
                        StringRedisTemplate redisTemplate) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generate a scoped JWT for an authenticated agent instance.
     */
    public String generateAgentToken(String agentId, String agentType,
                                     List<String> scopes, List<String> allowedMcpServers,
                                     String dataAccessLevel) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(AGENT_TOKEN_TTL_MINUTES));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(agentId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("type", "agent")
                .claim("agent_type", agentType)
                .claim("scopes", scopes)
                .claim("mcp_servers", allowedMcpServers)
                .claim("data_access_level", dataAccessLevel)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a session token for a verified customer.
     */
    public String generateCustomerToken(String customerId, String riskLevel, String channel) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(CUSTOMER_TOKEN_TTL_MINUTES));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(customerId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("type", "customer")
                .claim("risk_level", riskLevel)
                .claim("channel", channel)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate a token and return its claims. Returns null if invalid or blacklisted.
     */
    public Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Check if token has been blacklisted (revoked)
            String jti = claims.getId();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + jti))) {
                log.warn("Token {} has been revoked", jti);
                return null;
            }

            return claims;
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Refresh an existing valid token by issuing a new one with extended expiry.
     */
    public String refreshToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }

        // Blacklist the old token
        revokeToken(claims.getId(), getRemainingTtl(claims));

        String tokenType = claims.get("type", String.class);
        if ("agent".equals(tokenType)) {
            return generateAgentToken(
                    claims.getSubject(),
                    claims.get("agent_type", String.class),
                    getListClaim(claims, "scopes"),
                    getListClaim(claims, "mcp_servers"),
                    claims.get("data_access_level", String.class)
            );
        } else {
            return generateCustomerToken(
                    claims.getSubject(),
                    claims.get("risk_level", String.class),
                    claims.get("channel", String.class)
            );
        }
    }

    /**
     * Revoke a token by adding its JTI to the blacklist in Redis.
     */
    public void revokeToken(String jti, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                TOKEN_BLACKLIST_PREFIX + jti, "revoked", ttlSeconds, TimeUnit.SECONDS
        );
    }

    /**
     * Extract token type and expiration details for validation responses.
     */
    public Map<String, Object> extractTokenMetadata(Claims claims) {
        return Map.of(
                "subject", claims.getSubject(),
                "type", claims.get("type", String.class),
                "expiration", claims.getExpiration().toInstant().toString()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> getListClaim(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    private long getRemainingTtl(Claims claims) {
        long expiryEpoch = claims.getExpiration().getTime() / 1000;
        long now = Instant.now().getEpochSecond();
        return Math.max(expiryEpoch - now, 0);
    }
}
