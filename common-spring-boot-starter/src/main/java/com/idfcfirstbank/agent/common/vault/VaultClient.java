package com.idfcfirstbank.agent.common.vault;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Client that calls the Vault Policy Service to evaluate whether an agent action
 * is permitted under the current policy set.
 */
@Slf4j
@Component
public class VaultClient {

    private final RestTemplate restTemplate;
    private final String vaultBaseUrl;

    public VaultClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${agent.vault.base-url:http://vault-policy-service:8080}") String vaultBaseUrl) {
        this.vaultBaseUrl = vaultBaseUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Evaluate a policy for the given agent action.
     *
     * @param agentId  identifier of the agent requesting the action
     * @param action   the action being performed (e.g. "transfer", "viewBalance")
     * @param resource the target resource (e.g. "account:1234567890")
     * @param context  additional contextual attributes for the policy engine
     * @return the policy decision returned by the Vault Policy Service
     */
    public PolicyDecision evaluatePolicy(String agentId,
                                         String action,
                                         String resource,
                                         Map<String, Object> context) {
        String url = vaultBaseUrl + "/api/v1/policies/evaluate";

        Map<String, Object> requestBody = Map.of(
                "agentId", agentId,
                "action", action,
                "resource", resource,
                "context", context != null ? context : Map.of()
        );

        try {
            log.debug("Evaluating policy: agentId={}, action={}, resource={}", agentId, action, resource);
            PolicyDecision decision = restTemplate.postForObject(url, requestBody, PolicyDecision.class);

            if (decision == null) {
                log.error("Vault policy service returned null decision for agentId={}, action={}", agentId, action);
                return new PolicyDecision(PolicyDecision.Decision.DENY, "No decision received from vault", "SYSTEM");
            }

            log.info("Policy decision for agentId={}, action={}: {}", agentId, action, decision.decision());
            return decision;
        } catch (RestClientException ex) {
            log.error("Failed to evaluate policy via vault service: {}", ex.getMessage(), ex);
            // Fail-closed: deny when the policy service is unreachable
            return new PolicyDecision(
                    PolicyDecision.Decision.DENY,
                    "Policy service unavailable – fail-closed: " + ex.getMessage(),
                    "SYSTEM_FAILCLOSE"
            );
        }
    }
}
