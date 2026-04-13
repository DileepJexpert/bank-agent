package com.idfcfirstbank.agent.internalops.service;

import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.internalops.model.InternalOpsRequest;
import com.idfcfirstbank.agent.internalops.model.InternalOpsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalOpsServiceTest {

    @Mock
    private LlmRouter llmRouter;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private InternalOpsService internalOpsService;

    @Test
    void processQuery_vaultDeny_returnsAccessDenied() {
        InternalOpsRequest request = new InternalOpsRequest(
                "session-1", "EMP001", "generate report", "MIS_REPORT", 0.95, Map.of());

        when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new PolicyDecision(PolicyDecision.Decision.DENY, "Unauthorized", "POLICY-1"));

        InternalOpsResponse response = internalOpsService.processQuery(request);

        assertNotNull(response);
        assertTrue(response.message().contains("Access denied"));
        assertFalse(response.escalated());
    }

    @Test
    void processQuery_vaultEscalate_returnsEscalation() {
        InternalOpsRequest request = new InternalOpsRequest(
                "session-2", "EMP001", "sensitive query", "COMPLIANCE_QUERY", 0.9, Map.of());

        when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new PolicyDecision(PolicyDecision.Decision.ESCALATE, "Manager approval needed", "POLICY-2"));

        InternalOpsResponse response = internalOpsService.processQuery(request);

        assertNotNull(response);
        assertTrue(response.escalated());
        assertTrue(response.message().contains("manager approval"));
    }

    @Test
    void handleItHelpdesk_knownFaq_returnsStaticAnswer() {
        InternalOpsResponse response = internalOpsService.handleItHelpdesk("EMP001", "How do I reset my Finacle password?");

        assertNotNull(response);
        assertTrue(response.message().contains("Finacle"));
        assertTrue(response.message().contains("Forgot Password"));
    }

    @Test
    void handleItHelpdesk_vpnQuery_returnsVpnInstructions() {
        InternalOpsResponse response = internalOpsService.handleItHelpdesk("EMP001", "How to connect to VPN?");

        assertNotNull(response);
        assertTrue(response.message().contains("FortiClient"));
    }

    @Test
    void handleHrQuery_returnsHrPortalInfo() {
        InternalOpsResponse response = internalOpsService.handleHrQuery("EMP001", "How many leaves do I have?");

        assertNotNull(response);
        assertTrue(response.message().contains("HRMS"));
        assertTrue(response.message().contains("Leave Balance"));
    }

    @Test
    void getReconciliationStatus_mcpUnavailable_returnsFallback() {
        var status = internalOpsService.getReconciliationStatus();

        assertNotNull(status);
        assertEquals("MCP_UNAVAILABLE", status.status());
    }
}
