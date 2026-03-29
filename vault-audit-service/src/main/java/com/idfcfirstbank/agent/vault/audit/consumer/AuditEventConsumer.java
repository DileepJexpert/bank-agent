package com.idfcfirstbank.agent.vault.audit.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.vault.audit.model.AuditEventEntity;
import com.idfcfirstbank.agent.vault.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private static final Set<String> PII_FIELDS = Set.of(
            "customerId", "customer_id", "accountNumber", "account_number",
            "phoneNumber", "phone_number", "email", "panNumber", "pan_number",
            "aadhaarNumber", "aadhaar_number"
    );

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\d{10,18}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{10,13}");
    private static final Pattern PAN_PATTERN = Pattern.compile("[A-Z]{5}\\d{4}[A-Z]");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("\\d{12}");

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "vault.audit.events",
            groupId = "${spring.kafka.consumer.group-id:vault-audit-consumer}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAuditEvent(ConsumerRecord<String, String> record) {
        log.debug("Received audit event: key={}, partition={}, offset={}",
                record.key(), record.partition(), record.offset());

        try {
            JsonNode eventNode = objectMapper.readTree(record.value());

            AuditEventEntity entity = AuditEventEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(parseTimestamp(eventNode))
                    .agentId(getTextOrDefault(eventNode, "agentId", "unknown"))
                    .instanceId(getTextOrNull(eventNode, "instanceId"))
                    .customerId(maskPii(getTextOrNull(eventNode, "customerId")))
                    .action(getTextOrDefault(eventNode, "action", "unknown"))
                    .resource(getTextOrDefault(eventNode, "resource", "unknown"))
                    .policyResult(getTextOrDefault(eventNode, "decision", "UNKNOWN"))
                    .policyRef(getTextOrNull(eventNode, "policyRef"))
                    .requestHash(getTextOrNull(eventNode, "requestHash"))
                    .responseHash(getTextOrNull(eventNode, "responseHash"))
                    .latencyMs(getLongOrNull(eventNode, "evaluationTimeMs"))
                    .build();

            auditEventRepository.save(entity);
            log.debug("Persisted audit event: eventId={}, agent={}, action={}",
                    entity.getEventId(), entity.getAgentId(), entity.getAction());

        } catch (Exception e) {
            log.error("Failed to process audit event: key={}, error={}",
                    record.key(), e.getMessage(), e);
        }
    }

    /**
     * Masks PII fields before storage.
     * Applies pattern-based masking for known sensitive data formats.
     */
    private String maskPii(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        // Mask account numbers: show last 4 digits
        if (ACCOUNT_PATTERN.matcher(value).matches() && value.length() >= 8) {
            return "****" + value.substring(value.length() - 4);
        }

        // Mask phone numbers: show last 4 digits
        if (PHONE_PATTERN.matcher(value).matches()) {
            String digits = value.replaceAll("[^\\d]", "");
            return "****" + digits.substring(digits.length() - 4);
        }

        // Mask PAN: show first 2 and last 1
        if (PAN_PATTERN.matcher(value).matches()) {
            return value.substring(0, 2) + "****" + value.substring(value.length() - 1);
        }

        // Mask Aadhaar: show last 4
        if (AADHAAR_PATTERN.matcher(value).matches()) {
            return "********" + value.substring(8);
        }

        // For customer IDs that look like identifiers, mask middle portion
        if (value.length() > 8) {
            int visibleChars = 4;
            return value.substring(0, visibleChars) + "****"
                    + value.substring(value.length() - visibleChars);
        }

        return value;
    }

    private Instant parseTimestamp(JsonNode node) {
        String ts = getTextOrNull(node, "timestamp");
        if (ts != null) {
            try {
                return Instant.parse(ts);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp: {}", ts);
            }
        }
        return Instant.now();
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : defaultValue;
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
    }

    private Long getLongOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asLong() : null;
    }
}
