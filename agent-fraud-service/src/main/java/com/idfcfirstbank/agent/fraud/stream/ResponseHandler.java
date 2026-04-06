package com.idfcfirstbank.agent.fraud.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.fraud.entity.FraudEvent;
import com.idfcfirstbank.agent.fraud.model.FraudAlert;
import com.idfcfirstbank.agent.fraud.model.RiskLevel;
import com.idfcfirstbank.agent.fraud.repository.FraudEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResponseHandler {

    private static final String AGENT_ID = "fraud-detection-agent";
    private static final String INSTANCE_ID = "fraud-service-instance";

    private final FraudEventRepository fraudEventRepository;
    private final VaultClient vaultClient;
    private final AuditEventPublisher auditEventPublisher;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mcp.notification.url}")
    private String notificationMcpUrl;

    @Value("${mcp.payment-gateway.url}")
    private String paymentGatewayMcpUrl;

    @Value("${mcp.core-banking.url}")
    private String coreBankingMcpUrl;

    public void handleAlert(FraudAlert alert) {
        long startTime = System.currentTimeMillis();
        RiskLevel riskLevel = RiskLevel.valueOf(alert.riskLevel());

        log.info("Handling fraud alert: alertId={}, txnId={}, riskLevel={}",
                alert.alertId(), alert.txnId(), riskLevel);

        try {
            switch (riskLevel) {
                case LOW -> handleLow(alert);
                case MEDIUM -> handleMedium(alert);
                case HIGH -> handleHigh(alert);
                case CRITICAL -> handleCritical(alert);
            }
        } catch (Exception e) {
            log.error("Error handling fraud alert: alertId={}", alert.alertId(), e);
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        publishAuditEvent(alert, latencyMs);
    }

    private void handleLow(FraudAlert alert) {
        saveFraudEvent(alert);
        log.info("LOW risk alert saved for txn={}", alert.txnId());
    }

    private void handleMedium(FraudAlert alert) {
        saveFraudEvent(alert);
        sendSmsNotification(alert);
        log.info("MEDIUM risk alert processed for txn={}: saved + SMS sent", alert.txnId());
    }

    private void handleHigh(FraudAlert alert) {
        saveFraudEvent(alert);
        sendSmsNotification(alert);
        blockTransaction(alert);
        freezeAccount(alert);
        log.info("HIGH risk alert processed for txn={}: saved + SMS + blocked + frozen", alert.txnId());
    }

    private void handleCritical(FraudAlert alert) {
        saveFraudEvent(alert);
        sendSmsNotification(alert);
        blockTransaction(alert);
        freezeAccount(alert);
        escalateToFraudOps(alert);
        log.info("CRITICAL risk alert processed for txn={}: saved + SMS + blocked + frozen + escalated", alert.txnId());
    }

    private void saveFraudEvent(FraudAlert alert) {
        FraudEvent event = FraudEvent.builder()
                .txnId(alert.txnId())
                .customerId(alert.customerId())
                .riskScore(alert.riskScore())
                .riskLevel(alert.riskLevel())
                .actionTaken(alert.actionTaken())
                .createdAt(Instant.now())
                .build();

        fraudEventRepository.save(event);
    }

    private void sendSmsNotification(FraudAlert alert) {
        try {
            Map<String, Object> payload = Map.of(
                    "customerId", alert.customerId(),
                    "channel", "SMS",
                    "templateId", "FRAUD_ALERT",
                    "params", Map.of(
                            "txnId", alert.txnId(),
                            "riskLevel", alert.riskLevel(),
                            "amount", "Transaction flagged"
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(notificationMcpUrl + "/api/v1/notifications/send", request, String.class);
            log.info("SMS notification sent for txn={}", alert.txnId());
        } catch (Exception e) {
            log.error("Failed to send SMS notification for txn={}", alert.txnId(), e);
        }
    }

    private void blockTransaction(FraudAlert alert) {
        try {
            Map<String, Object> payload = Map.of(
                    "txnId", alert.txnId(),
                    "action", "BLOCK",
                    "reason", "Fraud detection - risk level: " + alert.riskLevel()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(paymentGatewayMcpUrl + "/api/v1/transactions/block", request, String.class);
            log.info("Transaction blocked for txn={}", alert.txnId());
        } catch (Exception e) {
            log.error("Failed to block transaction txn={}", alert.txnId(), e);
        }
    }

    private void freezeAccount(FraudAlert alert) {
        try {
            // Evaluate vault policy before freezing account
            PolicyDecision decision = vaultClient.evaluatePolicy(
                    AGENT_ID,
                    "FREEZE_ACCOUNT",
                    "account:" + alert.customerId(),
                    Map.of(
                            "txnId", alert.txnId(),
                            "riskScore", String.valueOf(alert.riskScore()),
                            "riskLevel", alert.riskLevel()
                    )
            );

            if (decision.decision() == PolicyDecision.Decision.DENY) {
                log.warn("Vault policy DENIED account freeze for customer={}: {}",
                        alert.customerId(), decision.reason());
                return;
            }

            if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
                log.info("Vault policy requires ESCALATION for account freeze, customer={}: {}",
                        alert.customerId(), decision.reason());
                escalateToFraudOps(alert);
                return;
            }

            Map<String, Object> payload = Map.of(
                    "customerId", alert.customerId(),
                    "action", "FREEZE",
                    "durationHours", 4,
                    "reason", "Fraud detection - risk level: " + alert.riskLevel(),
                    "policyRef", decision.policyRef()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(coreBankingMcpUrl + "/api/v1/accounts/freeze", request, String.class);
            log.info("Account frozen for customer={} (4 hours), policyRef={}",
                    alert.customerId(), decision.policyRef());
        } catch (Exception e) {
            log.error("Failed to freeze account for customer={}", alert.customerId(), e);
        }
    }

    private void escalateToFraudOps(FraudAlert alert) {
        try {
            Map<String, Object> payload = Map.of(
                    "customerId", alert.customerId(),
                    "channel", "INTERNAL",
                    "templateId", "FRAUD_ESCALATION",
                    "priority", "CRITICAL",
                    "params", Map.of(
                            "alertId", alert.alertId(),
                            "txnId", alert.txnId(),
                            "riskLevel", alert.riskLevel(),
                            "riskScore", String.valueOf(alert.riskScore()),
                            "mlScore", String.valueOf(alert.mlScore()),
                            "ruleScore", String.valueOf(alert.ruleScore()),
                            "behavioralScore", String.valueOf(alert.behavioralScore())
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(notificationMcpUrl + "/api/v1/notifications/escalate", request, String.class);
            log.info("Alert escalated to fraud ops team for txn={}, alertId={}", alert.txnId(), alert.alertId());
        } catch (Exception e) {
            log.error("Failed to escalate alert for txn={}", alert.txnId(), e);
        }
    }

    private void publishAuditEvent(FraudAlert alert, long latencyMs) {
        try {
            String requestPayload = objectMapper.writeValueAsString(Map.of(
                    "txnId", alert.txnId(),
                    "customerId", alert.customerId()
            ));
            String responsePayload = objectMapper.writeValueAsString(Map.of(
                    "alertId", alert.alertId(),
                    "riskScore", alert.riskScore(),
                    "riskLevel", alert.riskLevel(),
                    "actionTaken", alert.actionTaken()
            ));

            AuditEvent auditEvent = new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    AGENT_ID,
                    INSTANCE_ID,
                    alert.customerId(),
                    "FRAUD_DETECTION",
                    "transaction:" + alert.txnId(),
                    alert.riskLevel(),
                    requestPayload,
                    responsePayload,
                    latencyMs
            );

            auditEventPublisher.publish(auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event for alert={}", alert.alertId(), e);
        }
    }
}
