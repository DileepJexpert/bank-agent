package com.idfcfirstbank.agent.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Health indicator that checks connectivity to the vault-policy-service.
 * Only activated when {@code agent.vault.url} is configured.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agent.vault", name = "url")
public class VaultHealthIndicator implements HealthIndicator {

    private final String vaultUrl;
    private final RestTemplate restTemplate;

    public VaultHealthIndicator(
            @Value("${agent.vault.url}") String vaultUrl,
            RestTemplate restTemplate) {
        this.vaultUrl = vaultUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public Health health() {
        String healthEndpoint = vaultUrl + "/actuator/health";
        long startTime = System.currentTimeMillis();

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(healthEndpoint, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                        .withDetail("vaultUrl", vaultUrl)
                        .withDetail("responseTimeMs", responseTime)
                        .build();
            } else {
                return Health.down()
                        .withDetail("vaultUrl", vaultUrl)
                        .withDetail("statusCode", response.getStatusCode().value())
                        .withDetail("responseTimeMs", responseTime)
                        .build();
            }
        } catch (Exception ex) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.warn("Vault health check failed [url={}]: {}", healthEndpoint, ex.getMessage());
            return Health.down()
                    .withDetail("vaultUrl", vaultUrl)
                    .withDetail("responseTimeMs", responseTime)
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
