package com.idfcfirstbank.agent.vault.anomaly.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka message record representing an anomaly alert.
 */
public record AnomalyAlert(
        UUID alertId,
        AnomalyEvent.Severity severity,
        AnomalyEvent.AnomalyType anomalyType,
        String agentId,
        String instanceId,
        Map<String, Object> details,
        Instant detectedAt
) {

    public static AnomalyAlert of(
            AnomalyEvent.Severity severity,
            AnomalyEvent.AnomalyType anomalyType,
            String agentId,
            String instanceId,
            Map<String, Object> details
    ) {
        return new AnomalyAlert(
                UUID.randomUUID(),
                severity,
                anomalyType,
                agentId,
                instanceId,
                details,
                Instant.now()
        );
    }
}
