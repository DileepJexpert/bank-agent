package com.idfcfirstbank.agent.vault.anomaly.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableKafkaStreams
public class KafkaStreamsConfig {

    public static final String VAULT_AUDIT_EVENTS_TOPIC = "vault-audit-events";
    public static final String GATEWAY_ACCESS_LOG_TOPIC = "gateway-access-log";
    public static final String ANOMALY_ALERTS_TOPIC = "anomaly-alerts";

    @Value("${spring.kafka.streams.application-id}")
    private String applicationId;

    @Value("${spring.kafka.streams.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.streams.state-dir:/tmp/kafka-streams/vault-anomaly}")
    private String stateDir;

    @Bean(name = "defaultKafkaStreamsConfig")
    public KafkaStreamsConfiguration kafkaStreamsConfiguration() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 3);
        return new KafkaStreamsConfiguration(props);
    }

    @Bean
    public NewTopic anomalyAlertsTopic() {
        return TopicBuilder.name(ANOMALY_ALERTS_TOPIC)
                .partitions(6)
                .replicas(3)
                .build();
    }
}
