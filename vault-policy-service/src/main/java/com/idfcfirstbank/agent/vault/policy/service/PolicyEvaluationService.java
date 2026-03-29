package com.idfcfirstbank.agent.vault.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.vault.policy.model.PolicyEvaluationRequest;
import com.idfcfirstbank.agent.vault.policy.model.PolicyEvaluationResponse;
import com.idfcfirstbank.agent.vault.policy.model.PolicyEvaluationResponse.Decision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PolicyEvaluationService {

    private static final String CACHE_PREFIX = "policy:eval:";
    private static final long CACHE_TTL_SECONDS = 60;
    private static final String AUDIT_TOPIC = "vault.audit.events";

    private final RestClient opaClient;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${vault.opa.policy-path:/v1/data/agent/authz}")
    private String opaPolicyPath;

    public PolicyEvaluationService(
            @Value("${vault.opa.url:http://localhost:8181}") String opaUrl,
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.opaClient = RestClient.builder().baseUrl(opaUrl).build();
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates a policy request against OPA.
     * Checks Redis cache first, then calls OPA sidecar, and publishes the result to Kafka.
     */
    public PolicyEvaluationResponse evaluate(PolicyEvaluationRequest request) {
        Instant start = Instant.now();

        // Check cache for frequent evaluations
        String cacheKey = buildCacheKey(request);
        PolicyEvaluationResponse cached = getCachedDecision(cacheKey);
        if (cached != null) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.debug("Cache hit for policy evaluation: agent={}, action={}", request.agentId(), request.action());
            PolicyEvaluationResponse cachedResponse = new PolicyEvaluationResponse(
                    cached.decision(), cached.reason(), cached.policyRef(), elapsed);
            publishAuditEvent(request, cachedResponse);
            return cachedResponse;
        }

        // Call OPA for evaluation
        PolicyEvaluationResponse response;
        try {
            response = callOpa(request, start);
        } catch (Exception e) {
            log.error("OPA evaluation failed for agent={}, action={}: {}",
                    request.agentId(), request.action(), e.getMessage());
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            // Fail-closed: deny on OPA errors
            response = new PolicyEvaluationResponse(
                    Decision.DENY,
                    "Policy evaluation failed - fail-closed: " + e.getMessage(),
                    "system/fail-closed",
                    elapsed
            );
        }

        // Cache the decision
        cacheDecision(cacheKey, response);

        // Publish audit event
        publishAuditEvent(request, response);

        return response;
    }

    /**
     * Calls OPA sidecar/server for policy evaluation.
     */
    private PolicyEvaluationResponse callOpa(PolicyEvaluationRequest request, Instant start) {
        Map<String, Object> opaInput = Map.of("input", Map.of(
                "agent_id", request.agentId(),
                "agent_type", request.agentType(),
                "action", request.action(),
                "resource", request.resource(),
                "customer_id", request.customerId() != null ? request.customerId() : "",
                "context", request.context() != null ? request.context() : Map.of(),
                "timestamp", request.timestamp().toString()
        ));

        String responseBody = opaClient.post()
                .uri(opaPolicyPath)
                .header("Content-Type", "application/json")
                .body(opaInput)
                .retrieve()
                .body(String.class);

        long elapsed = Duration.between(start, Instant.now()).toMillis();

        return parseOpaResponse(responseBody, elapsed);
    }

    /**
     * Parses the OPA JSON response into a PolicyEvaluationResponse.
     */
    private PolicyEvaluationResponse parseOpaResponse(String responseBody, long elapsed) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.path("result");

            boolean allow = result.path("allow").asBoolean(false);
            boolean escalate = result.path("escalate").asBoolean(false);
            String reason = result.path("reason").asText("No reason provided");
            String policyRef = result.path("policy_ref").asText("unknown");

            Decision decision;
            if (escalate) {
                decision = Decision.ESCALATE;
            } else if (allow) {
                decision = Decision.ALLOW;
            } else {
                decision = Decision.DENY;
            }

            return new PolicyEvaluationResponse(decision, reason, policyRef, elapsed);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse OPA response: {}", e.getMessage());
            return new PolicyEvaluationResponse(
                    Decision.DENY, "Failed to parse policy response", "system/parse-error", elapsed);
        }
    }

    private String buildCacheKey(PolicyEvaluationRequest request) {
        return CACHE_PREFIX + request.agentId() + ":" + request.agentType()
                + ":" + request.action() + ":" + request.resource();
    }

    private PolicyEvaluationResponse getCachedDecision(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, PolicyEvaluationResponse.class);
            }
        } catch (Exception e) {
            log.warn("Cache read failed: {}", e.getMessage());
        }
        return null;
    }

    private void cacheDecision(String cacheKey, PolicyEvaluationResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Cache write failed: {}", e.getMessage());
        }
    }

    /**
     * Publishes policy evaluation result to Kafka audit topic.
     */
    private void publishAuditEvent(PolicyEvaluationRequest request, PolicyEvaluationResponse response) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "POLICY_EVALUATION",
                    "agentId", request.agentId(),
                    "agentType", request.agentType(),
                    "action", request.action(),
                    "resource", request.resource(),
                    "decision", response.decision().name(),
                    "reason", response.reason(),
                    "policyRef", response.policyRef(),
                    "evaluationTimeMs", response.evaluationTimeMs(),
                    "timestamp", Instant.now().toString()
            );
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(AUDIT_TOPIC, request.agentId(), payload);
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", e.getMessage());
        }
    }
}
