package com.idfcfirstbank.agent.vault.anomaly.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.vault.anomaly.config.KafkaStreamsConfig;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyAlert;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.AnomalyType;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.Severity;
import com.idfcfirstbank.agent.vault.anomaly.service.AnomalyResponseService;
import com.idfcfirstbank.agent.vault.anomaly.service.BaselineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Kafka Streams processor that analyzes agent behavior patterns.
 * <p>
 * Performs windowed aggregation to count actions per agent instance
 * within configurable time windows. Triggers anomaly alerts when:
 * <ul>
 *   <li>Action count exceeds the high-rate threshold</li>
 *   <li>An action type has never been seen for this agent type</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentBehaviorAnalyzer {

    private final ObjectMapper objectMapper;
    private final AnomalyResponseService anomalyResponseService;
    private final BaselineService baselineService;

    @Value("${agent.anomaly.high-rate-threshold:100}")
    private int highRateThreshold;

    @Value("${agent.anomaly.window-minutes:5}")
    private int windowMinutes;

    @Bean
    public KStream<String, String> agentBehaviorStream(StreamsBuilder streamsBuilder) {
        KStream<String, String> auditStream = streamsBuilder.stream(
                KafkaStreamsConfig.VAULT_AUDIT_EVENTS_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String())
        );

        // Windowed aggregation: count actions per agent instance
        auditStream
                .groupBy((key, value) -> extractAgentInstanceKey(value))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(windowMinutes)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("agent-action-counts")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()))
                .toStream()
                .foreach((windowedKey, count) -> {
                    if (count != null && count > highRateThreshold) {
                        String compositeKey = windowedKey.key();
                        String[] parts = compositeKey.split(":", 2);
                        String agentId = parts.length > 0 ? parts[0] : "unknown";
                        String instanceId = parts.length > 1 ? parts[1] : "unknown";

                        log.warn("High rate anomaly detected: agent={}, instance={}, count={} in {}min window",
                                agentId, instanceId, count, windowMinutes);

                        AnomalyAlert alert = AnomalyAlert.of(
                                Severity.HIGH,
                                AnomalyType.HIGH_RATE,
                                agentId,
                                instanceId,
                                Map.of(
                                        "actionCount", count,
                                        "windowMinutes", windowMinutes,
                                        "threshold", highRateThreshold
                                )
                        );
                        anomalyResponseService.handleAlert(alert);
                    }
                });

        // Unknown action type detection
        auditStream.foreach((key, value) -> {
            try {
                JsonNode node = objectMapper.readTree(value);
                String agentType = node.path("agentType").asText("");
                String actionType = node.path("actionType").asText("");
                String agentId = node.path("agentId").asText("unknown");
                String instanceId = node.path("instanceId").asText("unknown");

                if (!agentType.isEmpty() && !actionType.isEmpty()
                        && !baselineService.isKnownAction(agentType, actionType)) {
                    log.warn("Unknown action detected: agentType={}, actionType={}", agentType, actionType);

                    AnomalyAlert alert = AnomalyAlert.of(
                            Severity.MEDIUM,
                            AnomalyType.UNKNOWN_ACTION,
                            agentId,
                            instanceId,
                            Map.of(
                                    "agentType", agentType,
                                    "actionType", actionType,
                                    "message", "Action type not in known baseline for agent type"
                            )
                    );
                    anomalyResponseService.handleAlert(alert);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse audit event for action type analysis: {}", e.getMessage());
            }
        });

        return auditStream;
    }

    private String extractAgentInstanceKey(String eventJson) {
        try {
            JsonNode node = objectMapper.readTree(eventJson);
            String agentId = node.path("agentId").asText("unknown");
            String instanceId = node.path("instanceId").asText("unknown");
            return agentId + ":" + instanceId;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse audit event for key extraction: {}", e.getMessage());
            return "unknown:unknown";
        }
    }
}
