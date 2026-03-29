package com.idfcfirstbank.agent.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Utility for creating and validating JWT tokens used for service-to-service
 * authentication across the Agent Platform.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${agent.security.jwt.secret:default-secret-key-that-must-be-changed-in-production-env}")
    private String secret;

    @Value("${agent.security.jwt.expiration-ms:3600000}")
    private long expirationMs;

    @Value("${agent.security.jwt.issuer:idfc-agent-platform}")
    private String issuer;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Pad or hash the secret to guarantee a key length suitable for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(
                keyBytes.length >= 32 ? keyBytes : padKey(keyBytes));
    }

    /**
     * Generate a signed JWT containing the supplied claims.
     *
     * @param claims arbitrary claims to embed in the token body
     * @return a compact, URL-safe JWT string
     */
    public String generateToken(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate the token signature and expiration.
     *
     * @param token compact JWT string
     * @return {@code true} when the token is structurally valid, correctly signed, and not expired
     */
    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Parse and return the claims embedded in a valid token.
     *
     * @param token compact JWT string
     * @return parsed {@link Claims}
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private static byte[] padKey(byte[] original) {
        byte[] padded = new byte[32];
        System.arraycopy(original, 0, padded, 0, original.length);
        return padded;
    }
}
