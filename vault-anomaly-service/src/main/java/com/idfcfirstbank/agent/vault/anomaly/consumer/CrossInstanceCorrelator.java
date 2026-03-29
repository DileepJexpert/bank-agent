package com.idfcfirstbank.agent.vault.anomaly.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.vault.anomaly.config.KafkaStreamsConfig;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyAlert;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.AnomalyType;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.Severity;
import com.idfcfirstbank.agent.vault.anomaly.service.AnomalyResponseService;
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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kafka Streams processor that detects coordinated access patterns.
 * <p>
 * Monitors the gateway access log to count unique agent instances
 * accessing the same customer within a 1-minute sliding window.
 * If the number of distinct instances exceeds the configured threshold,
 * a COORDINATED_ACCESS anomaly alert is raised.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossInstanceCorrelator {

    private static final String INSTANCE_DELIMITER = "|";

    private final ObjectMapper objectMapper;
    private final AnomalyResponseService anomalyResponseService;

    @Value("${agent.anomaly.coordinated-access-threshold:5}")
    private int coordinatedAccessThreshold;

    @Bean
    public KStream<String, String> crossInstanceStream(StreamsBuilder streamsBuilder) {
        KStream<String, String> accessLogStream = streamsBuilder.stream(
                KafkaStreamsConfig.GATEWAY_ACCESS_LOG_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String())
        );

        // Key by customerId, aggregate unique instanceIds per 1-minute window
        accessLogStream
                .groupBy((key, value) -> extractCustomerId(value))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .aggregate(
                        () -> "",
                        (customerId, eventJson, existingInstances) -> {
                            String instanceId = extractInstanceId(eventJson);
                            if (instanceId.isEmpty()) {
                                return existingInstances;
                            }
                            // Track unique instances using delimiter-separated string
                            Set<String> instances = existingInstances.isEmpty()
                                    ? new java.util.HashSet<>()
                                    : new java.util.HashSet<>(Arrays.asList(
                                    existingInstances.split("\\" + INSTANCE_DELIMITER)));
                            instances.add(instanceId);
                            return String.join(INSTANCE_DELIMITER, instances);
                        },
                        Materialized.<String, String, WindowStore<Bytes, byte[]>>as("customer-instance-counts")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String())
                )
                .toStream()
                .foreach((windowedKey, instanceList) -> {
                    if (instanceList == null || instanceList.isEmpty()) {
                        return;
                    }
                    Set<String> uniqueInstances = Arrays.stream(
                                    instanceList.split("\\" + INSTANCE_DELIMITER))
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());

                    if (uniqueInstances.size() > coordinatedAccessThreshold) {
                        String customerId = windowedKey.key();

                        log.warn("Coordinated access anomaly: customerId={}, uniqueInstances={}",
                                customerId, uniqueInstances.size());

                        AnomalyAlert alert = AnomalyAlert.of(
                                Severity.CRITICAL,
                                AnomalyType.COORDINATED_ACCESS,
                                "multi-agent",
                                "multiple",
                                Map.of(
                                        "customerId", customerId,
                                        "uniqueInstanceCount", uniqueInstances.size(),
                                        "instanceIds", uniqueInstances,
                                        "threshold", coordinatedAccessThreshold,
                                        "windowSeconds", 60
                                )
                        );
                        anomalyResponseService.handleAlert(alert);
                    }
                });

        return accessLogStream;
    }

    private String extractCustomerId(String eventJson) {
        try {
            JsonNode node = objectMapper.readTree(eventJson);
            return node.path("customerId").asText("unknown");
        } catch (JsonProcessingException e) {
            log.error("Failed to parse access log event for customerId: {}", e.getMessage());
            return "unknown";
        }
    }

    private String extractInstanceId(String eventJson) {
        try {
            JsonNode node = objectMapper.readTree(eventJson);
            return node.path("instanceId").asText("");
        } catch (JsonProcessingException e) {
            log.error("Failed to parse access log event for instanceId: {}", e.getMessage());
            return "";
        }
    }
}
