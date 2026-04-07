package com.idfcfirstbank.agent.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.fraud.entity.FraudEvent;
import com.idfcfirstbank.agent.fraud.model.FraudAlert;
import com.idfcfirstbank.agent.fraud.repository.FraudEventRepository;
import com.idfcfirstbank.agent.fraud.stream.ResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseHandlerTest {

    @Mock
    private FraudEventRepository fraudEventRepository;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ResponseHandler responseHandler;

    @Captor
    private ArgumentCaptor<FraudEvent> fraudEventCaptor;

    @Captor
    private ArgumentCaptor<AuditEvent> auditEventCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(responseHandler, "notificationMcpUrl", "http://localhost:8100");
        ReflectionTestUtils.setField(responseHandler, "paymentGatewayMcpUrl", "http://localhost:8101");
        ReflectionTestUtils.setField(responseHandler, "coreBankingMcpUrl", "http://localhost:8102");
    }

    @Test
    @DisplayName("LOW risk: should only save to database")
    void lowRiskSavesOnly() {
        FraudAlert alert = createAlert("LOW", 0.2);

        responseHandler.handleAlert(alert);

        verify(fraudEventRepository).save(any(FraudEvent.class));
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), any());
        verify(auditEventPublisher).publish(any(AuditEvent.class));
    }

    @Test
    @DisplayName("MEDIUM risk: should save and send SMS notification")
    void mediumRiskSavesAndNotifies() {
        FraudAlert alert = createAlert("MEDIUM", 0.55);

        when(restTemplate.postForEntity(
                contains("/api/v1/notifications/send"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("sent"));

        responseHandler.handleAlert(alert);

        verify(fraudEventRepository).save(any(FraudEvent.class));
        verify(restTemplate).postForEntity(
                eq("http://localhost:8100/api/v1/notifications/send"),
                any(HttpEntity.class), eq(String.class));
        verify(auditEventPublisher).publish(any(AuditEvent.class));
    }

    @Test
    @DisplayName("HIGH risk: should save, SMS, block transaction, and freeze account")
    void highRiskFullResponse() {
        FraudAlert alert = createAlert("HIGH", 0.8);

        PolicyDecision allowDecision = new PolicyDecision(
                PolicyDecision.Decision.ALLOW, "Policy allows freeze", "POL-001");
        when(vaultClient.evaluatePolicy(anyString(), eq("FREEZE_ACCOUNT"), anyString(), any(Map.class)))
                .thenReturn(allowDecision);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        responseHandler.handleAlert(alert);

        verify(fraudEventRepository).save(any(FraudEvent.class));
        // SMS notification
        verify(restTemplate).postForEntity(
                eq("http://localhost:8100/api/v1/notifications/send"),
                any(HttpEntity.class), eq(String.class));
        // Block transaction
        verify(restTemplate).postForEntity(
                eq("http://localhost:8101/api/v1/transactions/block"),
                any(HttpEntity.class), eq(String.class));
        // Freeze account
        verify(restTemplate).postForEntity(
                eq("http://localhost:8102/api/v1/accounts/freeze"),
                any(HttpEntity.class), eq(String.class));
        verify(vaultClient).evaluatePolicy(anyString(), eq("FREEZE_ACCOUNT"), anyString(), any(Map.class));
        verify(auditEventPublisher).publish(any(AuditEvent.class));
    }

    @Test
    @DisplayName("HIGH risk: should not freeze when vault denies policy")
    void highRiskVaultDeny() {
        FraudAlert alert = createAlert("HIGH", 0.8);

        PolicyDecision denyDecision = new PolicyDecision(
                PolicyDecision.Decision.DENY, "Policy denies freeze for this account type", "POL-002");
        when(vaultClient.evaluatePolicy(anyString(), eq("FREEZE_ACCOUNT"), anyString(), any(Map.class)))
                .thenReturn(denyDecision);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        responseHandler.handleAlert(alert);

        verify(fraudEventRepository).save(any(FraudEvent.class));
        // SMS and block still happen
        verify(restTemplate).postForEntity(
                eq("http://localhost:8100/api/v1/notifications/send"),
                any(HttpEntity.class), eq(String.class));
        verify(restTemplate).postForEntity(
                eq("http://localhost:8101/api/v1/transactions/block"),
                any(HttpEntity.class), eq(String.class));
        // Freeze should NOT happen
        verify(restTemplate, never()).postForEntity(
                eq("http://localhost:8102/api/v1/accounts/freeze"),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("HIGH risk: vault ESCALATE should trigger escalation instead of freeze")
    void highRiskVaultEscalate() {
        FraudAlert alert = createAlert("HIGH", 0.8);

        PolicyDecision escalateDecision = new PolicyDecision(
                PolicyDecision.Decision.ESCALATE, "Requires manual approval", "POL-003");
        when(vaultClient.evaluatePolicy(anyString(), eq("FREEZE_ACCOUNT"), anyString(), any(Map.class)))
                .thenReturn(escalateDecision);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        responseHandler.handleAlert(alert);

        // Freeze should NOT happen but escalation should
        verify(restTemplate, never()).postForEntity(
                eq("http://localhost:8102/api/v1/accounts/freeze"),
                any(HttpEntity.class), eq(String.class));
        verify(restTemplate).postForEntity(
                eq("http://localhost:8100/api/v1/notifications/escalate"),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("CRITICAL risk: should do everything HIGH does plus escalate to fraud ops")
    void criticalRiskFullResponse() {
        FraudAlert alert = createAlert("CRITICAL", 0.95);

        PolicyDecision allowDecision = new PolicyDecision(
                PolicyDecision.Decision.ALLOW, "Policy allows", "POL-001");
        when(vaultClient.evaluatePolicy(anyString(), eq("FREEZE_ACCOUNT"), anyString(), any(Map.class)))
                .thenReturn(allowDecision);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        responseHandler.handleAlert(alert);

        verify(fraudEventRepository).save(any(FraudEvent.class));
        // SMS
        verify(restTemplate).postForEntity(
                eq("http://localhost:8100/api/v1/notifications/send"),
                any(HttpEntity.class), eq(String.class));
        // Block
        verify(restTemplate).postForEntity(
                eq("http://localhost:8101/api/v1/transactions/block"),
                any(HttpEntity.class), eq(String.class));
        // Freeze
        verify(restTemplate).postForEntity(
                eq("http://localhost:8102/api/v1/accounts/freeze"),
                any(HttpEntity.class), eq(String.class));
        // Escalation
        verify(restTemplate).postForEntity(
                eq("http://localhost:8100/api/v1/notifications/escalate"),
                any(HttpEntity.class), eq(String.class));
        verify(auditEventPublisher).publish(any(AuditEvent.class));
    }

    @Test
    @DisplayName("Should save correct fraud event fields to database")
    void savedFraudEventHasCorrectFields() {
        FraudAlert alert = createAlert("MEDIUM", 0.55);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        responseHandler.handleAlert(alert);

        verify(fraudEventRepository).save(fraudEventCaptor.capture());
        FraudEvent savedEvent = fraudEventCaptor.getValue();

        assertThat(savedEvent.getTxnId()).isEqualTo("TXN-TEST");
        assertThat(savedEvent.getCustomerId()).isEqualTo("CUST-TEST");
        assertThat(savedEvent.getRiskScore()).isEqualTo(0.55);
        assertThat(savedEvent.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(savedEvent.getActionTaken()).isEqualTo("LOG_AND_NOTIFY");
        assertThat(savedEvent.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should always publish audit event regardless of risk level")
    void auditEventAlwaysPublished() {
        FraudAlert lowAlert = createAlert("LOW", 0.1);

        responseHandler.handleAlert(lowAlert);

        verify(auditEventPublisher).publish(auditEventCaptor.capture());
        AuditEvent auditEvent = auditEventCaptor.getValue();

        assertThat(auditEvent.customerId()).isEqualTo("CUST-TEST");
        assertThat(auditEvent.action()).isEqualTo("FRAUD_DETECTION");
        assertThat(auditEvent.resource()).isEqualTo("transaction:TXN-TEST");
    }

    private FraudAlert createAlert(String riskLevel, double riskScore) {
        String actionTaken = switch (riskLevel) {
            case "LOW" -> "LOG_ONLY";
            case "MEDIUM" -> "LOG_AND_NOTIFY";
            case "HIGH" -> "BLOCK_AND_FREEZE";
            case "CRITICAL" -> "BLOCK_FREEZE_AND_ESCALATE";
            default -> "UNKNOWN";
        };

        return new FraudAlert(
                "ALERT-001",
                "TXN-TEST",
                "CUST-TEST",
                riskScore,
                riskLevel,
                riskScore * 0.4,
                riskScore * 0.3,
                riskScore * 0.3,
                actionTaken,
                Instant.now()
        );
    }
}
