package com.idfcfirstbank.agent.wealth;

import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.wealth.model.WealthRequest;
import com.idfcfirstbank.agent.wealth.model.WealthResponse;
import com.idfcfirstbank.agent.wealth.service.WealthService;
import com.idfcfirstbank.agent.wealth.tools.WealthTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.idfcfirstbank.agent.common.llm.LlmRouter;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WealthService}.
 * Tests each intent handler, SEBI disclaimer inclusion, and vault policy enforcement.
 */
@ExtendWith(MockitoExtension.class)
class WealthServiceTest {

    @Mock
    private LlmRouter llmRouter;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private WealthTools wealthTools;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<AuditEvent> auditEventCaptor;

    @InjectMocks
    private WealthService wealthService;

    private PolicyDecision allowDecision;
    private PolicyDecision denyDecision;
    private PolicyDecision escalateDecision;

    @BeforeEach
    void setUp() throws Exception {
        var instanceIdField = WealthService.class.getDeclaredField("instanceId");
        instanceIdField.setAccessible(true);
        instanceIdField.set(wealthService, "test-instance");

        allowDecision = new PolicyDecision(PolicyDecision.Decision.ALLOW, "Permitted", "policy-001");
        denyDecision = new PolicyDecision(PolicyDecision.Decision.DENY, "Access denied for this resource", "policy-002");
        escalateDecision = new PolicyDecision(PolicyDecision.Decision.ESCALATE, "Requires human approval", "policy-003");
    }

    // --- Vault Policy Tests ---

    @Nested
    @DisplayName("Vault Policy Enforcement")
    class VaultPolicyTests {

        @Test
        @DisplayName("Should deny request when vault policy returns DENY")
        void shouldDenyWhenVaultDenies() {
            WealthRequest request = new WealthRequest(
                    "session-1", "CUST001", "Show my portfolio", "PORTFOLIO_SUMMARY", 0.95, Map.of());

            when(vaultClient.evaluatePolicy(eq("wealth-agent"), eq("PORTFOLIO_SUMMARY"),
                    eq("customer:CUST001"), anyMap())).thenReturn(denyDecision);

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.escalated()).isFalse();
            assertThat(response.message()).contains("unable to process");
            assertThat(response.message()).contains("Access denied");
            verify(wealthTools, never()).getPortfolioRaw(anyString());
            verify(auditEventPublisher).publish(any(AuditEvent.class));
        }

        @Test
        @DisplayName("Should escalate request when vault policy returns ESCALATE")
        void shouldEscalateWhenVaultEscalates() {
            WealthRequest request = new WealthRequest(
                    "session-2", "CUST002", "Show my portfolio", "PORTFOLIO_SUMMARY", 0.90, Map.of());

            when(vaultClient.evaluatePolicy(eq("wealth-agent"), eq("PORTFOLIO_SUMMARY"),
                    eq("customer:CUST002"), anyMap())).thenReturn(escalateDecision);

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.escalated()).isTrue();
            assertThat(response.message()).contains("additional verification");
            assertThat(response.message()).contains("wealth specialist");
            verify(wealthTools, never()).getPortfolioRaw(anyString());
            verify(auditEventPublisher).publish(any(AuditEvent.class));
        }
    }

    // --- Portfolio Summary Tests ---

    @Nested
    @DisplayName("Portfolio Summary (Tier 1)")
    class PortfolioSummaryTests {

        @Test
        @DisplayName("Should return portfolio summary with SEBI disclaimer")
        void shouldReturnPortfolioWithDisclaimer() {
            WealthRequest request = new WealthRequest(
                    "session-3", "CUST003", "Show my portfolio", "PORTFOLIO_SUMMARY", 0.95, Map.of());

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.getPortfolioRaw("CUST003"))
                    .thenReturn("{mutualFunds: [{name: 'HDFC Top 100', value: 150000}], totalValue: 500000}");

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.intent()).isEqualTo("PORTFOLIO_SUMMARY");
            assertThat(response.escalated()).isFalse();
            assertThat(response.message()).contains(WealthService.SEBI_DISCLAIMER);
            assertThat(response.message()).contains("CUST003");
            assertThat(response.disclaimer()).isEqualTo(WealthService.SEBI_DISCLAIMER);
            verify(wealthTools).getPortfolioRaw("CUST003");
        }
    }

    // --- SIP Management Tests ---

    @Nested
    @DisplayName("SIP Management (Tier 1)")
    class SipManagementTests {

        @Test
        @DisplayName("Should view SIP details when no action specified")
        void shouldViewSipDetails() {
            WealthRequest request = new WealthRequest(
                    "session-4", "CUST004", "Show my SIPs", "SIP_MANAGEMENT", 0.92, Map.of());

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.getSipDetailsRaw("CUST004"))
                    .thenReturn("{sips: [{fundName: 'Axis Bluechip', amount: 5000, frequency: 'MONTHLY'}]}");

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.intent()).isEqualTo("SIP_MANAGEMENT");
            assertThat(response.message()).contains(WealthService.SEBI_DISCLAIMER);
            assertThat(response.disclaimer()).isEqualTo(WealthService.SEBI_DISCLAIMER);
            verify(wealthTools).getSipDetailsRaw("CUST004");
        }

        @Test
        @DisplayName("Should create SIP with provided parameters")
        void shouldCreateSip() {
            Map<String, Object> params = new HashMap<>();
            params.put("action", "CREATE");
            params.put("fundName", "ICICI Prudential Bluechip");
            params.put("amount", "10000");
            params.put("frequency", "MONTHLY");

            WealthRequest request = new WealthRequest(
                    "session-5", "CUST005", "Create a SIP", "SIP_MANAGEMENT", 0.88, params);

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.createSipRaw("CUST005", "ICICI Prudential Bluechip", 10000.0, "MONTHLY", ""))
                    .thenReturn("{status: 'SUCCESS', sipId: 'SIP-12345'}");

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.intent()).isEqualTo("SIP_MANAGEMENT");
            assertThat(response.message()).contains(WealthService.SEBI_DISCLAIMER);
            verify(wealthTools).createSipRaw("CUST005", "ICICI Prudential Bluechip", 10000.0, "MONTHLY", "");
        }

        @Test
        @DisplayName("Should cancel SIP with provided parameters")
        void shouldCancelSip() {
            Map<String, Object> params = new HashMap<>();
            params.put("action", "CANCEL");
            params.put("sipId", "SIP-12345");
            params.put("reason", "No longer needed");

            WealthRequest request = new WealthRequest(
                    "session-6", "CUST006", "Cancel my SIP", "SIP_MANAGEMENT", 0.91, params);

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.cancelSipRaw("CUST006", "SIP-12345", "No longer needed"))
                    .thenReturn("{status: 'SUCCESS', message: 'SIP cancelled'}");

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.intent()).isEqualTo("SIP_MANAGEMENT");
            verify(wealthTools).cancelSipRaw("CUST006", "SIP-12345", "No longer needed");
        }
    }

    // --- Investment Query Tests ---

    @Nested
    @DisplayName("Investment Query (Tier 2 - LLM)")
    class InvestmentQueryTests {

        @Test
        @DisplayName("Should include SEBI disclaimer in investment query response")
        void shouldIncludeSebiDisclaimer() {
            WealthRequest request = new WealthRequest(
                    "session-7", "CUST007", "Which mutual funds should I invest in?",
                    "INVESTMENT_QUERY", 0.85, Map.of());

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.getRiskProfileRaw("CUST007"))
                    .thenReturn("{riskCategory: 'MODERATE', score: 55}");

            when(llmRouter.chat(anyString(), anyString())).thenReturn(
                    "Based on your moderate risk profile, I recommend diversified equity funds.");

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.intent()).isEqualTo("INVESTMENT_QUERY");
            assertThat(response.message()).contains(WealthService.SEBI_DISCLAIMER);
            assertThat(response.message()).contains("moderate risk profile");
            assertThat(response.disclaimer()).isEqualTo(WealthService.SEBI_DISCLAIMER);
            verify(wealthTools).getRiskProfileRaw("CUST007");
        }

        @Test
        @DisplayName("Should check risk profile before investment recommendation")
        void shouldCheckRiskProfileFirst() {
            WealthRequest request = new WealthRequest(
                    "session-8", "CUST008", "Suggest some high-return funds",
                    "INVESTMENT_QUERY", 0.80, Map.of());

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.getRiskProfileRaw("CUST008"))
                    .thenReturn("{riskCategory: 'CONSERVATIVE', score: 25}");

            when(llmRouter.chat(anyString(), anyString())).thenReturn("mocked wealth response");

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.message()).contains(WealthService.SEBI_DISCLAIMER);
            verify(wealthTools).getRiskProfileRaw("CUST008");
        }

        @Test
        @DisplayName("Should handle LLM error gracefully")
        void shouldHandleLlmError() {
            WealthRequest request = new WealthRequest(
                    "session-9", "CUST009", "Investment advice",
                    "INVESTMENT_QUERY", 0.75, Map.of());

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.getRiskProfileRaw("CUST009"))
                    .thenReturn("{riskCategory: 'MODERATE', score: 50}");
            when(llmRouter.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM service unavailable"));

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.message()).contains("encountered an issue");
            assertThat(response.escalated()).isFalse();
        }
    }

    // --- Insurance Status Tests ---

    @Nested
    @DisplayName("Insurance Status (Tier 0)")
    class InsuranceStatusTests {

        @Test
        @DisplayName("Should return insurance status without SEBI disclaimer")
        void shouldReturnInsuranceStatus() {
            Map<String, Object> params = new HashMap<>();
            params.put("policyNumber", "POL-001");

            WealthRequest request = new WealthRequest(
                    "session-10", "CUST010", "Check my insurance status",
                    "INSURANCE_STATUS", 0.97, params);

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.getInsuranceStatusRaw("CUST010", "POL-001"))
                    .thenReturn("{policyNumber: 'POL-001', status: 'ACTIVE', premiumDue: '2026-05-15', amount: 12000}");

            WealthResponse response = wealthService.processQuery(request);

            assertThat(response.intent()).isEqualTo("INSURANCE_STATUS");
            assertThat(response.message()).contains("CUST010");
            assertThat(response.message()).contains("POL-001");
            assertThat(response.disclaimer()).isNull();
            assertThat(response.escalated()).isFalse();
            verify(wealthTools).getInsuranceStatusRaw("CUST010", "POL-001");
        }
    }

    // --- Audit Event Tests ---

    @Nested
    @DisplayName("Audit Event Publishing")
    class AuditEventTests {

        @Test
        @DisplayName("Should publish audit event after successful operation")
        void shouldPublishAuditEventOnSuccess() {
            WealthRequest request = new WealthRequest(
                    "session-11", "CUST011", "Show my portfolio",
                    "PORTFOLIO_SUMMARY", 0.95, Map.of());

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision);
            when(wealthTools.getPortfolioRaw("CUST011"))
                    .thenReturn("{totalValue: 100000}");

            wealthService.processQuery(request);

            verify(auditEventPublisher).publish(auditEventCaptor.capture());
            AuditEvent event = auditEventCaptor.getValue();
            assertThat(event.agentId()).isEqualTo("wealth-agent");
            assertThat(event.customerId()).isEqualTo("CUST011");
            assertThat(event.action()).isEqualTo("PORTFOLIO_SUMMARY");
            assertThat(event.policyResult()).isEqualTo("ALLOW");
            assertThat(event.latencyMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should publish audit event on vault deny")
        void shouldPublishAuditEventOnDeny() {
            WealthRequest request = new WealthRequest(
                    "session-12", "CUST012", "Show portfolio",
                    "PORTFOLIO_SUMMARY", 0.90, Map.of());

            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(denyDecision);

            wealthService.processQuery(request);

            verify(auditEventPublisher).publish(auditEventCaptor.capture());
            AuditEvent event = auditEventCaptor.getValue();
            assertThat(event.policyResult()).isEqualTo("DENY");
        }
    }
}
