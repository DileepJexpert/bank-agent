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
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Kafka Streams processor that detects prompt injection attempts
 * in customer input text from audit events.
 * <p>
 * Uses pattern-based detection for adversarial inputs including
 * known injection phrases, encoded payloads, and excessive special characters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptInjectionDetector {

    private final ObjectMapper objectMapper;
    private final AnomalyResponseService anomalyResponseService;

    /**
     * Known prompt injection phrases (case-insensitive matching).
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+you\\s+are", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?prior", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(all\\s+)?previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override\\s+(your\\s+)?instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+(a|an)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("do\\s+not\\s+follow\\s+(your\\s+)?rules", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new\\s+instructions\\s*:", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Pattern to detect Base64-encoded content (minimum 20 chars of valid Base64).
     */
    private static final Pattern BASE64_PATTERN = Pattern.compile(
            "[A-Za-z0-9+/]{20,}={0,2}"
    );

    /**
     * Pattern to detect hex-encoded content (minimum 20 hex chars).
     */
    private static final Pattern HEX_ENCODED_PATTERN = Pattern.compile(
            "(?:0x)?[0-9a-fA-F]{20,}"
    );

    /**
     * Pattern to detect excessive special characters (more than 10 consecutive).
     */
    private static final Pattern EXCESSIVE_SPECIAL_CHARS_PATTERN = Pattern.compile(
            "[^a-zA-Z0-9\\s]{10,}"
    );

    /**
     * Minimum input length worth scanning -- very short inputs are unlikely injections.
     */
    private static final int MIN_SCAN_LENGTH = 10;

    @Bean
    public KStream<String, String> promptInjectionStream(StreamsBuilder streamsBuilder) {
        KStream<String, String> auditStream = streamsBuilder.stream(
                KafkaStreamsConfig.VAULT_AUDIT_EVENTS_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String())
        );

        auditStream.foreach((key, value) -> analyzeForInjection(value));

        return auditStream;
    }

    private void analyzeForInjection(String eventJson) {
        try {
            JsonNode node = objectMapper.readTree(eventJson);
            String customerInput = extractCustomerInput(node);

            if (customerInput == null || customerInput.length() < MIN_SCAN_LENGTH) {
                return;
            }

            String agentId = node.path("agentId").asText("unknown");
            String instanceId = node.path("instanceId").asText("unknown");

            // Check for known injection phrases
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(customerInput).find()) {
                    publishInjectionAlert(agentId, instanceId, "pattern_match",
                            Map.of(
                                    "pattern", pattern.pattern(),
                                    "inputSnippet", truncate(customerInput, 200)
                            ));
                    return;
                }
            }

            // Check for Base64-encoded injection attempts
            if (BASE64_PATTERN.matcher(customerInput).find()) {
                String decoded = tryDecodeBase64(customerInput);
                if (decoded != null && containsInjectionPhrase(decoded)) {
                    publishInjectionAlert(agentId, instanceId, "base64_encoded_injection",
                            Map.of(
                                    "encoding", "base64",
                                    "inputSnippet", truncate(customerInput, 200)
                            ));
                    return;
                }
            }

            // Check for hex-encoded injection attempts
            if (HEX_ENCODED_PATTERN.matcher(customerInput).find()) {
                String decoded = tryDecodeHex(customerInput);
                if (decoded != null && containsInjectionPhrase(decoded)) {
                    publishInjectionAlert(agentId, instanceId, "hex_encoded_injection",
                            Map.of(
                                    "encoding", "hex",
                                    "inputSnippet", truncate(customerInput, 200)
                            ));
                    return;
                }
            }

            // Check for excessive special characters (obfuscation attempts)
            if (EXCESSIVE_SPECIAL_CHARS_PATTERN.matcher(customerInput).find()) {
                publishInjectionAlert(agentId, instanceId, "excessive_special_chars",
                        Map.of(
                                "reason", "Excessive special characters detected",
                                "inputSnippet", truncate(customerInput, 200)
                        ));
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse audit event for prompt injection analysis: {}", e.getMessage());
        }
    }

    private void publishInjectionAlert(String agentId, String instanceId,
                                       String detectionMethod, Map<String, Object> extraDetails) {
        log.warn("Prompt injection detected: agent={}, instance={}, method={}",
                agentId, instanceId, detectionMethod);

        Map<String, Object> details = new java.util.HashMap<>(extraDetails);
        details.put("detectionMethod", detectionMethod);

        AnomalyAlert alert = AnomalyAlert.of(
                Severity.CRITICAL,
                AnomalyType.PROMPT_INJECTION,
                agentId,
                instanceId,
                Map.copyOf(details)
        );
        anomalyResponseService.handleAlert(alert);
    }

    /**
     * Extracts customer input text from the audit event.
     * Checks multiple possible field names where user input may reside.
     */
    private String extractCustomerInput(JsonNode node) {
        for (String field : List.of("customerInput", "userInput", "input", "query", "message", "requestBody")) {
            JsonNode fieldNode = node.path(field);
            if (!fieldNode.isMissingNode() && fieldNode.isTextual()) {
                return fieldNode.asText();
            }
        }
        // Check nested request object
        JsonNode request = node.path("request");
        if (!request.isMissingNode()) {
            for (String field : List.of("body", "input", "message", "text")) {
                JsonNode fieldNode = request.path(field);
                if (!fieldNode.isMissingNode() && fieldNode.isTextual()) {
                    return fieldNode.asText();
                }
            }
        }
        return null;
    }

    private boolean containsInjectionPhrase(String text) {
        String lower = text.toLowerCase();
        return lower.contains("ignore previous")
                || lower.contains("system prompt")
                || lower.contains("you are now")
                || lower.contains("pretend you are")
                || lower.contains("disregard prior")
                || lower.contains("override instructions");
    }

    private String tryDecodeBase64(String input) {
        try {
            var matcher = BASE64_PATTERN.matcher(input);
            while (matcher.find()) {
                String candidate = matcher.group();
                byte[] decoded = Base64.getDecoder().decode(candidate);
                String text = new String(decoded);
                // Only return if the decoded result looks like readable text
                if (text.chars().allMatch(c -> c >= 32 && c < 127)) {
                    return text;
                }
            }
        } catch (IllegalArgumentException e) {
            // Not valid Base64
        }
        return null;
    }

    private String tryDecodeHex(String input) {
        try {
            var matcher = HEX_ENCODED_PATTERN.matcher(input);
            while (matcher.find()) {
                String hex = matcher.group();
                if (hex.startsWith("0x")) {
                    hex = hex.substring(2);
                }
                if (hex.length() % 2 != 0) {
                    continue;
                }
                byte[] bytes = new byte[hex.length() / 2];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                String text = new String(bytes);
                if (text.chars().allMatch(c -> c >= 32 && c < 127)) {
                    return text;
                }
            }
        } catch (NumberFormatException e) {
            // Not valid hex
        }
        return null;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
