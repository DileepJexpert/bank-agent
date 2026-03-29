package com.idfcfirstbank.agent.vault.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that provides JSON Web Key Set (JWKS) containing active public keys
 * used for JWT verification. Keys are loaded from the jwt_keys database table
 * and cached with periodic refresh.
 */
@Service
@Slf4j
public class JwksService {

    private static final long CACHE_REFRESH_INTERVAL_MS = 300_000; // 5 minutes

    private final JdbcTemplate jdbcTemplate;
    private final AtomicReference<Map<String, Object>> cachedJwks = new AtomicReference<>(Map.of("keys", List.of()));
    private volatile long lastRefreshTime = 0;

    @Value("${vault.jwks.fallback-enabled:true}")
    private boolean fallbackEnabled;

    public JwksService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the JWKS containing all active, non-expired public keys.
     * Results are cached for 5 minutes to reduce database load.
     */
    public Map<String, Object> getJwks() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > CACHE_REFRESH_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastRefreshTime > CACHE_REFRESH_INTERVAL_MS) {
                    refreshJwks();
                    lastRefreshTime = System.currentTimeMillis();
                }
            }
        }
        return cachedJwks.get();
    }

    /**
     * Refreshes the JWKS cache by loading active keys from the database.
     */
    private void refreshJwks() {
        try {
            List<Map<String, Object>> keys = new ArrayList<>();

            jdbcTemplate.query(
                    "SELECT key_id, public_key, algorithm FROM jwt_keys WHERE is_active = true AND expires_at > NOW()",
                    rs -> {
                        try {
                            String keyId = rs.getString("key_id");
                            String publicKeyPem = rs.getString("public_key");
                            String algorithm = rs.getString("algorithm");

                            Map<String, Object> jwk = convertToJwk(keyId, publicKeyPem, algorithm);
                            if (jwk != null) {
                                keys.add(jwk);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to process JWT key from database: {}", e.getMessage());
                        }
                    }
            );

            Map<String, Object> jwks = new LinkedHashMap<>();
            jwks.put("keys", keys);
            cachedJwks.set(jwks);

            log.info("Refreshed JWKS cache with {} active keys", keys.size());
        } catch (Exception e) {
            log.error("Failed to refresh JWKS from database: {}", e.getMessage(), e);
            // Keep the existing cached JWKS on failure
        }
    }

    /**
     * Converts a PEM-encoded public key to JWK format (RFC 7517).
     */
    private Map<String, Object> convertToJwk(String keyId, String publicKeyPem, String algorithm) {
        try {
            String cleanedKey = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey rsaPublicKey = (RSAPublicKey) keyFactory.generatePublic(spec);

            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "RSA");
            jwk.put("kid", keyId);
            jwk.put("use", "sig");
            jwk.put("alg", algorithm);
            jwk.put("n", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rsaPublicKey.getModulus().toByteArray()));
            jwk.put("e", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rsaPublicKey.getPublicExponent().toByteArray()));

            return jwk;
        } catch (Exception e) {
            log.error("Failed to convert public key to JWK format for keyId={}: {}", keyId, e.getMessage());
            return null;
        }
    }
}
