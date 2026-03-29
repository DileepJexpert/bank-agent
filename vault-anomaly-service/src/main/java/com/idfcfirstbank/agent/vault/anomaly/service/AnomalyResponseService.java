package com.idfcfirstbank.agent.vault.anomaly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.vault.anomaly.config.KafkaStreamsConfig;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyAlert;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.Severity;
import com.idfcfirstbank.agent.vault.anomaly.repository.AnomalyEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Automated anomaly response service that routes alerts based on severity.
 * <p>
 * Response actions by severity:
 * <ul>
 *   <li>LOW: Log only, increment metric counter</li>
 *   <li>MEDIUM: Log + publish throttle event to gateway via Kafka</li>
 *   <li>HIGH: Log + call K8s API to label pod as quarantined (mocked)</li>
 *   <li>CRITICAL: Log + alert + would scale deployment to 0 (mocked)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyResponseService {

    private static final String THROTTLE_EVENTS_TOPIC = "gateway-throttle-events";

    private final AnomalyEventRepository anomalyEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final Map<Severity, Counter> anomalyCounters = new EnumMap<>(Severity.class);

    @PostConstruct
    void initMetrics() {
        for (Severity severity : Severity.values()) {
            anomalyCounters.put(severity,
                    Counter.builder("vault.anomaly.detected")
                            .tag("severity", severity.name())
                            .description("Number of anomalies detected by severity")
                            .register(meterRegistry));
        }
    }

    /**
     * Handles an anomaly alert by persisting it, incrementing metrics,
     * and executing the appropriate automated response based on severity.
     */
    public void handleAlert(AnomalyAlert alert) {
        // Persist the anomaly event
        AnomalyEvent event = AnomalyEvent.builder()
                .alertId(alert.alertId())
                .severity(alert.severity())
                .anomalyType(alert.anomalyType())
                .agentId(alert.agentId())
                .instanceId(alert.instanceId())
                .details(alert.details())
                .detectedAt(alert.detectedAt())
                .build();

        anomalyEventRepository.save(event);

        // Increment metric counter
        anomalyCounters.get(alert.severity()).increment();

        // Publish alert to Kafka for downstream consumers
        publishAlertToKafka(alert);

        // Execute severity-specific response
        switch (alert.severity()) {
            case LOW -> handleLow(alert);
            case MEDIUM -> handleMedium(alert);
            case HIGH -> handleHigh(alert);
            case CRITICAL -> handleCritical(alert);
        }
    }

    private void handleLow(AnomalyAlert alert) {
        log.info("LOW anomaly recorded: type={}, agent={}, instance={}, alertId={}",
                alert.anomalyType(), alert.agentId(), alert.instanceId(), alert.alertId());
    }

    private void handleMedium(AnomalyAlert alert) {
        log.warn("MEDIUM anomaly detected: type={}, agent={}, instance={}, alertId={}",
                alert.anomalyType(), alert.agentId(), alert.instanceId(), alert.alertId());

        // Publish throttle event for the API gateway to consume
        Map<String, Object> throttleEvent = Map.of(
                "action", "THROTTLE",
                "agentId", alert.agentId(),
                "instanceId", alert.instanceId(),
                "anomalyType", alert.anomalyType().name(),
                "alertId", alert.alertId().toString(),
                "reason", "Medium severity anomaly detected: " + alert.anomalyType()
        );

        kafkaTemplate.send(THROTTLE_EVENTS_TOPIC, alert.agentId(), throttleEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish throttle event for alertId={}: {}",
                                alert.alertId(), ex.getMessage());
                    } else {
                        log.info("Throttle event published for agent={}, alertId={}",
                                alert.agentId(), alert.alertId());
                    }
                });
    }

    private void handleHigh(AnomalyAlert alert) {
        log.error("HIGH anomaly detected: type={}, agent={}, instance={}, alertId={}",
                alert.anomalyType(), alert.agentId(), alert.instanceId(), alert.alertId());

        // Publish throttle event
        handleMedium(alert);

        // Mock: Label pod as quarantined via K8s API
        quarantinePod(alert.instanceId(), alert.alertId().toString());
    }

    private void handleCritical(AnomalyAlert alert) {
        log.error("CRITICAL anomaly detected: type={}, agent={}, instance={}, alertId={} - IMMEDIATE ACTION REQUIRED",
                alert.anomalyType(), alert.agentId(), alert.instanceId(), alert.alertId());

        // Publish throttle event
        handleMedium(alert);

        // Mock: Quarantine the pod
        quarantinePod(alert.instanceId(), alert.alertId().toString());

        // Mock: Scale deployment to 0 replicas
        scaleDeploymentToZero(alert.agentId(), alert.alertId().toString());
    }

    private void publishAlertToKafka(AnomalyAlert alert) {
        kafkaTemplate.send(KafkaStreamsConfig.ANOMALY_ALERTS_TOPIC, alert.agentId(), alert)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish anomaly alert to Kafka: alertId={}, error={}",
                                alert.alertId(), ex.getMessage());
                    }
                });
    }

    /**
     * Mock: Labels a Kubernetes pod as quarantined.
     * In production, this would call the K8s API to add a label
     * (e.g., vault.security/quarantined=true) to the pod, which
     * network policies would use to restrict traffic.
     */
    private void quarantinePod(String instanceId, String alertId) {
        log.warn("[MOCK K8S] Quarantining pod: instanceId={}, alertId={} - " +
                        "Would label pod with vault.security/quarantined=true",
                instanceId, alertId);
        // TODO: Implement K8s API call
        // kubernetesClient.pods()
        //     .withName(instanceId)
        //     .edit(pod -> new PodBuilder(pod)
        //         .editMetadata()
        //         .addToLabels("vault.security/quarantined", "true")
        //         .endMetadata()
        //         .build());
    }

    /**
     * Mock: Scales a Kubernetes deployment to 0 replicas.
     * In production, this would call the K8s API to set replicas=0
     * for the agent's deployment, effectively shutting it down.
     */
    private void scaleDeploymentToZero(String agentId, String alertId) {
        log.warn("[MOCK K8S] Scaling deployment to 0: agentId={}, alertId={} - " +
                        "Would set replicas=0 for deployment",
                agentId, alertId);
        // TODO: Implement K8s API call
        // kubernetesClient.apps().deployments()
        //     .withName(agentId + "-deployment")
        //     .scale(0);
    }
}
