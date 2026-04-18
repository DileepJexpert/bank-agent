package com.idfcfirstbank.agent.collections;

import com.idfcfirstbank.agent.collections.model.CollectionsRequest;
import com.idfcfirstbank.agent.collections.model.CollectionsResponse;
import com.idfcfirstbank.agent.collections.repository.CollectionsInteractionRepository;
import com.idfcfirstbank.agent.collections.service.CollectionsService;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CollectionsService}.
 * Tests each intent handler, vault policy enforcement, discount escalation, and payment link generation.
 */
@ExtendWith(MockitoExtension.class)
class CollectionsServiceTest {

    @Mock
    private LlmRouter llmRouter;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private CollectionsInteractionRepository interactionRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CollectionsService collectionsService;

    private static final String CUSTOMER_ID = "CUST-001";
    private static final String SESSION_ID = "session-123";
    private static final String LOAN_ID = "LOAN-456";
    private static final BigDecimal OVERDUE_AMOUNT = new BigDecimal("50000.00");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(collectionsService, "paymentBaseUrl",
                "https://pay.idfcfirstbank.com/collections");
        ReflectionTestUtils.setField(collectionsService, "instanceId",
                "agent-collections-service-test");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private CollectionsRequest buildRequest(String intent) {
        return buildRequest(intent, null);
    }

    private CollectionsRequest buildRequest(String intent, Map<String, Object> parameters) {
        return new CollectionsRequest(
                SESSION_ID,
                CUSTOMER_ID,
                "I need help with my overdue loan",
                intent,
                0.95,
                LOAN_ID,
                OVERDUE_AMOUNT,
                parameters
        );
    }

    private PolicyDecision allowDecision() {
        return new PolicyDecision(PolicyDecision.Decision.ALLOW, "Policy allows action", "POLICY-001");
    }

    private PolicyDecision denyDecision() {
        return new PolicyDecision(PolicyDecision.Decision.DENY, "Action not permitted", "POLICY-002");
    }

    private PolicyDecision escalateDecision() {
        return new PolicyDecision(PolicyDecision.Decision.ESCALATE,
                "Requires human review", "POLICY-003");
    }

    // ── PAYMENT_PLAN intent tests ──

    @Nested
    @DisplayName("PAYMENT_PLAN intent")
    class PaymentPlanTests {

        @Test
        @DisplayName("Should process payment plan with LLM when vault allows")
        void shouldProcessPaymentPlanWhenAllowed() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision());
            when(llmRouter.chat(anyString(), anyString())).thenReturn(
                    "Here are your restructured EMI options for loan LOAN-456...");

            CollectionsResponse response = collectionsService.processQuery(buildRequest("PAYMENT_PLAN"));

            assertThat(response.sessionId()).isEqualTo(SESSION_ID);
            assertThat(response.intent()).isEqualTo("PAYMENT_PLAN");
            assertThat(response.escalated()).isFalse();
            assertThat(response.message()).contains("restructured EMI options");
            verify(interactionRepository).save(any());
            verify(auditEventPublisher).publish(any());
        }

        @Test
        @DisplayName("Should deny payment plan when vault denies")
        void shouldDenyPaymentPlanWhenVaultDenies() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(denyDecision());

            CollectionsResponse response = collectionsService.processQuery(buildRequest("PAYMENT_PLAN"));

            assertThat(response.message()).contains("Unable to process");
            assertThat(response.escalated()).isFalse();
            verify(llmRouter, never()).chat(anyString(), anyString());
        }
    }

    // ── SETTLEMENT_OFFER intent tests ──

    @Nested
    @DisplayName("SETTLEMENT_OFFER intent")
    class SettlementOfferTests {

        @Test
        @DisplayName("Should process settlement with discount up to vault-approved limit")
        void shouldProcessSettlementWithApprovedDiscount() {
            // 10% discount - within typical vault-approved limit
            Map<String, Object> params = Map.of("requestedDiscountPct", "10");
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision());
            when(llmRouter.chat(anyString(), anyString())).thenReturn(
                    "Your settlement amount is INR 45,000.00 with a 10% discount.");

            CollectionsResponse response = collectionsService.processQuery(
                    buildRequest("SETTLEMENT_OFFER", params));

            assertThat(response.intent()).isEqualTo("SETTLEMENT_OFFER");
            assertThat(response.escalated()).isFalse();
            assertThat(response.settlementAmount()).isEqualByComparingTo(new BigDecimal("45000.00"));
            assertThat(response.discountPercent()).isEqualByComparingTo(new BigDecimal("10"));
            verify(interactionRepository).save(any());
        }

        @Test
        @DisplayName("Should escalate when vault escalates for high discount (e.g., >15%)")
        void shouldEscalateForHighDiscount() {
            // 20% discount - vault policy returns ESCALATE because it exceeds threshold
            Map<String, Object> params = Map.of("requestedDiscountPct", "20");
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(escalateDecision());

            CollectionsResponse response = collectionsService.processQuery(
                    buildRequest("SETTLEMENT_OFFER", params));

            assertThat(response.escalated()).isTrue();
            assertThat(response.message()).contains("senior collections officer");
            verify(llmRouter, never()).chat(anyString(), anyString());
        }

        @Test
        @DisplayName("Should deny settlement when vault denies")
        void shouldDenySettlementWhenVaultDenies() {
            Map<String, Object> params = Map.of("requestedDiscountPct", "5");
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(denyDecision());

            CollectionsResponse response = collectionsService.processQuery(
                    buildRequest("SETTLEMENT_OFFER", params));

            assertThat(response.message()).contains("Unable to process");
            assertThat(response.escalated()).isFalse();
            verify(llmRouter, never()).chat(anyString(), anyString());
        }

        @Test
        @DisplayName("Should reject settlement with invalid overdue amount")
        void shouldRejectSettlementWithInvalidAmount() {
            CollectionsRequest request = new CollectionsRequest(
                    SESSION_ID, CUSTOMER_ID, "settle my loan", "SETTLEMENT_OFFER",
                    0.95, LOAN_ID, BigDecimal.ZERO, Map.of("requestedDiscountPct", "10"));

            CollectionsResponse response = collectionsService.processQuery(request);

            assertThat(response.message()).contains("Invalid overdue amount");
        }
    }

    // ── PAYMENT_NOW intent tests ──

    @Nested
    @DisplayName("PAYMENT_NOW intent")
    class PaymentNowTests {

        @Test
        @DisplayName("Should generate payment link without LLM")
        void shouldGeneratePaymentLink() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision());

            CollectionsResponse response = collectionsService.processQuery(buildRequest("PAYMENT_NOW"));

            assertThat(response.intent()).isEqualTo("PAYMENT_NOW");
            assertThat(response.escalated()).isFalse();
            assertThat(response.paymentLink()).isNotNull();
            assertThat(response.paymentLink()).startsWith("https://pay.idfcfirstbank.com/collections/pay?token=");
            assertThat(response.paymentLink()).contains("customerId=" + CUSTOMER_ID);
            assertThat(response.paymentLink()).contains("loanId=" + LOAN_ID);
            assertThat(response.paymentLink()).contains("amount=" + OVERDUE_AMOUNT.toPlainString());
            assertThat(response.settlementAmount()).isEqualByComparingTo(OVERDUE_AMOUNT);
            verify(llmRouter, never()).chat(anyString(), anyString());
            verify(interactionRepository).save(any());
        }

        @Test
        @DisplayName("Should deny payment link when vault denies")
        void shouldDenyPaymentLinkWhenVaultDenies() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(denyDecision());

            CollectionsResponse response = collectionsService.processQuery(buildRequest("PAYMENT_NOW"));

            assertThat(response.paymentLink()).isNull();
            assertThat(response.message()).contains("Unable to process");
        }

        @Test
        @DisplayName("Should escalate payment link when vault escalates")
        void shouldEscalatePaymentLinkWhenVaultEscalates() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(escalateDecision());

            CollectionsResponse response = collectionsService.processQuery(buildRequest("PAYMENT_NOW"));

            assertThat(response.escalated()).isTrue();
            assertThat(response.paymentLink()).isNull();
        }
    }

    // ── Intent routing tests ──

    @Nested
    @DisplayName("Intent routing")
    class IntentRoutingTests {

        @Test
        @DisplayName("Should route to general handler for unknown intent")
        void shouldRouteToGeneralForUnknownIntent() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision());
            when(llmRouter.chat(anyString(), anyString())).thenReturn("How can I help you with your account?");

            CollectionsResponse response = collectionsService.processQuery(buildRequest("UNKNOWN"));

            assertThat(response.intent()).isEqualTo("GENERAL");
        }

        @Test
        @DisplayName("Should handle null intent as GENERAL")
        void shouldHandleNullIntentAsGeneral() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision());
            when(llmRouter.chat(anyString(), anyString())).thenReturn("How can I help you?");

            CollectionsResponse response = collectionsService.processQuery(buildRequest(null));

            assertThat(response.intent()).isEqualTo("GENERAL");
        }
    }

    // ── Contact count tracking tests ──

    @Nested
    @DisplayName("Contact count tracking")
    class ContactCountTests {

        @Test
        @DisplayName("Should increment contact count on successful operations")
        void shouldIncrementContactCount() {
            when(vaultClient.evaluatePolicy(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(allowDecision());
            when(llmRouter.chat(anyString(), anyString())).thenReturn("Payment plan details...");
            when(valueOperations.increment(anyString())).thenReturn(1L);

            collectionsService.processQuery(buildRequest("PAYMENT_PLAN"));

            verify(valueOperations).increment(argThat(key -> key.startsWith("collections:contact_count:")));
        }
    }
}
