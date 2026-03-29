package com.idfcfirstbank.agent.common.kafka;

import com.idfcfirstbank.agent.common.model.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes {@link AuditEvent} records to the {@code vault.audit.events} Kafka topic.
 * All agent actions that pass through vault policy evaluation should be audited via this publisher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    private static final String TOPIC = "vault.audit.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish an audit event asynchronously.
     *
     * @param event the audit event to publish
     */
    public void publish(AuditEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, event.agentId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish audit event [eventId={}, agentId={}]: {}",
                        event.eventId(), event.agentId(), ex.getMessage(), ex);
            } else {
                log.debug("Audit event published [eventId={}, partition={}, offset={}]",
                        event.eventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
