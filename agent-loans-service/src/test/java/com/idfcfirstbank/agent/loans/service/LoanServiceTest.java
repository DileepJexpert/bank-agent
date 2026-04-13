package com.idfcfirstbank.agent.loans.service;

import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.loans.model.LoanRequest;
import com.idfcfirstbank.agent.loans.model.LoanResponse;
import com.idfcfirstbank.agent.loans.tools.LoanTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.idfcfirstbank.agent.common.llm.LlmRouter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LlmRouter llmRouter;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private LoanTools loanTools;

    @InjectMocks
    private LoanService loanService;

    @Test
    void processQuery_vaultDeny_returnsErrorMessage() {
        LoanRequest request = new LoanRequest(
                "session-1", "CUST001", "Check loan eligibility",
                "LOAN_ELIGIBILITY", 0.95, Map.of());

        when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new PolicyDecision(PolicyDecision.Decision.DENY, "Blacklisted customer", "POLICY-1"));

        LoanResponse response = loanService.processQuery(request);

        assertNotNull(response);
        assertTrue(response.message().contains("unable to process"));
        assertFalse(response.escalated());
    }

    @Test
    void processQuery_vaultEscalate_returnsEscalation() {
        LoanRequest request = new LoanRequest(
                "session-2", "CUST002", "Check my loan",
                "LOAN_ELIGIBILITY", 0.9, Map.of());

        when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new PolicyDecision(PolicyDecision.Decision.ESCALATE, "High value", "POLICY-2"));

        LoanResponse response = loanService.processQuery(request);

        assertNotNull(response);
        assertTrue(response.escalated());
        assertTrue(response.message().contains("specialist"));
    }

    @Test
    void processQuery_emiQuery_tier0_noLlm() {
        LoanRequest request = new LoanRequest(
                "session-3", "CUST003", "What is my EMI?",
                "LOAN_EMI_QUERY", 0.98,
                Map.of("loanId", "LN001", "principal", 5000000.0, "annualRate", 8.5, "tenureMonths", 240));

        when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new PolicyDecision(PolicyDecision.Decision.ALLOW, "OK", "POLICY-3"));
        when(loanTools.getLoanDetailsRaw(anyString(), anyString()))
                .thenReturn("{\"loanId\":\"LN001\",\"status\":\"ACTIVE\"}");

        LoanResponse response = loanService.processQuery(request);

        assertNotNull(response);
        assertEquals("LOAN_EMI_QUERY", response.intent());
        assertTrue(response.message().contains("EMI"));
        assertFalse(response.requiresApproval());
    }
}
