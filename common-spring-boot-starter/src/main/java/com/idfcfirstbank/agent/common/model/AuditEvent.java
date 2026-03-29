package com.idfcfirstbank.agent.common.model;

import java.time.Instant;

/**
 * Immutable audit event record published to Kafka for compliance and observability.
 *
 * @param eventId         unique identifier for this audit event
 * @param timestamp       when the event occurred
 * @param agentId         identifier of the agent that triggered the action
 * @param instanceId      runtime instance (pod/container) identifier
 * @param customerId      masked customer identifier
 * @param action          the action that was performed
 * @param resource        the resource the action was performed on
 * @param policyResult    outcome of the vault policy evaluation (ALLOW/DENY/ESCALATE)
 * @param requestPayload  sanitised snapshot of the inbound request
 * @param responsePayload sanitised snapshot of the outbound response
 * @param latencyMs       end-to-end latency of the action in milliseconds
 */
public record AuditEvent(
        String eventId,
        Instant timestamp,
        String agentId,
        String instanceId,
        String customerId,
        String action,
        String resource,
        String policyResult,
        String requestPayload,
        String responsePayload,
        long latencyMs
) {
}
