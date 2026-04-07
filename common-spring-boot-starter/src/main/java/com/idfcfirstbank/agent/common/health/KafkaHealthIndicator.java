package com.idfcfirstbank.agent.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Health indicator that checks Kafka broker connectivity.
 * Only activated when {@link KafkaTemplate} is on the classpath.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 5;

    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            int brokerCount = clusterResult.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();
            String clusterId = clusterResult.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("brokerCount", brokerCount)
                    .build();
        } catch (Exception ex) {
            log.warn("Kafka health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
