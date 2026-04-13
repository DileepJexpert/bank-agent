package com.idfcfirstbank.agent.collections.service;

import com.idfcfirstbank.agent.collections.entity.CollectionsInteraction;
import com.idfcfirstbank.agent.collections.model.CollectionsRequest;
import com.idfcfirstbank.agent.collections.model.CollectionsResponse;
import com.idfcfirstbank.agent.collections.repository.CollectionsInteractionRepository;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Main service for the Collections Agent. Orchestrates tiered processing of collection
 * operations with Spring AI for conversational negotiation and Vault for policy enforcement.
 * <p>
 * Processing tiers:
 * <ul>
 *   <li><b>Tier 0 (PAYMENT_NOW)</b>: No LLM - generate payment link via pure Java</li>
 *   <li><b>Tier 2 (PAYMENT_PLAN)</b>: LLM-assisted negotiation of restructured EMI plan</li>
 *   <li><b>Tier 2 (SETTLEMENT_OFFER)</b>: LLM-assisted settlement with vault-governed discount</li>
 * </ul>
 * <p>
 * Every operation goes through Vault policy check and produces an AuditEvent.
 * Redis is used to track weekly contact counts per customer (RBI compliance).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionsService {

    private static final String AGENT_ID = "collections-agent";
    private static final String CONTACT_COUNT_KEY_PREFIX = "collections:contact_count:";
    private static final Duration CONTACT_COUNT_TTL = Duration.ofDays(7);

    private static final String SYSTEM_PROMPT = """
            You are the Collections Agent for IDFC First Bank. You handle loan collection
            and recovery operations with empathy and professionalism, always complying with
            RBI guidelines for debt collection.

            Your capabilities:
            - Negotiate restructured EMI payment plans for overdue accounts
            - Calculate and present settlement offers with applicable discounts
            - Guide customers through immediate payment options
            - Explain overdue consequences and available remedies

            MANDATORY RULES:
            1. Every outbound call MUST begin with: "This is IDFC First Bank AI assistant. This call is recorded per RBI guidelines."
            2. Never use threatening or abusive language. Be firm but empathetic.
            3. Respect RBI-mandated contact frequency limits (max 3 contacts per week per customer).
            4. Do not contact customers outside permitted hours (9 AM to 6 PM, Monday to Saturday).
            5. Always present the overdue amount, applicable charges, and available resolution options.
            6. Settlement discount percentages are governed by vault policy - do not promise specific discounts.
            7. Format all currency amounts in INR with proper formatting.
            8. If the customer disputes the debt or requests escalation, comply immediately.
            """;

    private final LlmRouter llmRouter;
    private final VaultClient vaultClient;
    private final AuditEventPublisher auditEventPublisher;
    private final CollectionsInteractionRepository interactionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${agent.collections.payment-base-url:https://pay.idfcfirstbank.com/collections}")
    private String paymentBaseUrl;

    @Value("${spring.application.name:agent-collections-service}")
    private String instanceId;

    /**
     * Route the inbound request to the appropriate handler based on detected intent.
     */
    public CollectionsResponse processQuery(CollectionsRequest request) {
        String intent = request.intent() != null ? request.intent().toUpperCase() : "GENERAL";

        return switch (intent) {
            case "PAYMENT_PLAN" -> handlePaymentPlan(request);
            case "SETTLEMENT_OFFER" -> handleSettlementOffer(request);
            case "PAYMENT_NOW" -> handlePayNow(request);
            default -> handleGeneral(request);
        };
    }

    /**
     * Tier 2: Negotiate a restructured EMI payment plan using LlmRouter.
     */
    public CollectionsResponse handlePaymentPlan(CollectionsRequest request) {
        long startTime = System.currentTimeMillis();
        String action = "PAYMENT_PLAN";

        // Vault policy check
        PolicyDecision decision = evaluatePolicy(request.customerId(), action, request);
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault denied PAYMENT_PLAN: customerId={}, reason={}", request.customerId(), decision.reason());
            publishAudit(request, action, decision, null, startTime);
            return CollectionsResponse.denied(request.sessionId(), decision.reason(), action);
        }
        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Vault escalated PAYMENT_PLAN: customerId={}", request.customerId());
            publishAudit(request, action, decision, null, startTime);
            return CollectionsResponse.escalated(request.sessionId(), action);
        }

        // Increment contact count
        incrementContactCount(request.customerId());

        try {
            String userPrompt = buildPaymentPlanPrompt(request);

            String llmResponse = llmRouter.chat(SYSTEM_PROMPT, userPrompt);

            // Record the interaction
            CollectionsInteraction interaction = CollectionsInteraction.builder()
                    .customerId(request.customerId())
                    .callTimestamp(Instant.now())
                    .outcome("PAYMENT_PLAN_OFFERED")
                    .offerMade("Restructured EMI plan")
                    .transcript(llmResponse)
                    .build();
            interactionRepository.save(interaction);

            CollectionsResponse response = new CollectionsResponse(
                    request.sessionId(),
                    llmResponse,
                    action,
                    false,
                    null,
                    null,
                    null
            );

            publishAudit(request, action, decision, response.message(), startTime);
            return response;

        } catch (Exception e) {
            log.error("Error processing payment plan: customerId={}", request.customerId(), e);
            publishAudit(request, action, decision, "ERROR: " + e.getMessage(), startTime);
            return new CollectionsResponse(
                    request.sessionId(),
                    "We encountered an issue preparing your payment plan. "
                            + "A collections officer will contact you within 24 hours.",
                    action,
                    false,
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * Tier 2: Calculate settlement offer with vault-governed discount.
     * The discount threshold is NOT hardcoded - vault policy decides whether the discount is allowed.
     */
    public CollectionsResponse handleSettlementOffer(CollectionsRequest request) {
        long startTime = System.currentTimeMillis();
        String action = "SETTLEMENT_OFFER";

        BigDecimal overdueAmount = request.overdueAmount();
        if (overdueAmount == null || overdueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return CollectionsResponse.denied(request.sessionId(),
                    "Invalid overdue amount for settlement calculation.", action);
        }

        // Determine the requested discount from parameters
        BigDecimal requestedDiscountPct = BigDecimal.ZERO;
        if (request.parameters() != null && request.parameters().containsKey("requestedDiscountPct")) {
            requestedDiscountPct = new BigDecimal(
                    String.valueOf(request.parameters().get("requestedDiscountPct")));
        }

        // Vault policy check with discount context - vault decides if the discount is permissible
        Map<String, Object> policyContext = new HashMap<>();
        policyContext.put("overdueAmount", overdueAmount.toString());
        policyContext.put("requestedDiscountPct", requestedDiscountPct.toString());
        policyContext.put("loanId", request.loanId() != null ? request.loanId() : "");
        if (request.parameters() != null && request.parameters().containsKey("daysOverdue")) {
            policyContext.put("daysOverdue", String.valueOf(request.parameters().get("daysOverdue")));
        }

        PolicyDecision decision = vaultClient.evaluatePolicy(
                AGENT_ID,
                action.toLowerCase(),
                "customer:" + request.customerId(),
                policyContext
        );

        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault denied SETTLEMENT_OFFER: customerId={}, reason={}",
                    request.customerId(), decision.reason());
            publishAudit(request, action, decision, null, startTime);
            return CollectionsResponse.denied(request.sessionId(), decision.reason(), action);
        }
        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Vault escalated SETTLEMENT_OFFER: customerId={}, requestedDiscount={}%",
                    request.customerId(), requestedDiscountPct);
            publishAudit(request, action, decision, null, startTime);
            return CollectionsResponse.escalated(request.sessionId(), action);
        }

        // Increment contact count
        incrementContactCount(request.customerId());

        try {
            // Calculate settlement amount with the approved discount
            BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                    requestedDiscountPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal settlementAmount = overdueAmount.multiply(discountMultiplier)
                    .setScale(2, RoundingMode.HALF_UP);

            String userPrompt = buildSettlementPrompt(request, requestedDiscountPct, settlementAmount);

            String llmResponse = llmRouter.chat(SYSTEM_PROMPT, userPrompt);

            // Record the interaction
            CollectionsInteraction interaction = CollectionsInteraction.builder()
                    .customerId(request.customerId())
                    .callTimestamp(Instant.now())
                    .outcome("SETTLEMENT_OFFERED")
                    .offerMade("Settlement at " + requestedDiscountPct + "% discount")
                    .discountPct(requestedDiscountPct)
                    .transcript(llmResponse)
                    .build();
            interactionRepository.save(interaction);

            CollectionsResponse response = new CollectionsResponse(
                    request.sessionId(),
                    llmResponse,
                    action,
                    false,
                    null,
                    settlementAmount,
                    requestedDiscountPct
            );

            publishAudit(request, action, decision, response.message(), startTime);
            return response;

        } catch (Exception e) {
            log.error("Error processing settlement offer: customerId={}", request.customerId(), e);
            publishAudit(request, action, decision, "ERROR: " + e.getMessage(), startTime);
            return new CollectionsResponse(
                    request.sessionId(),
                    "We encountered an issue calculating your settlement offer. "
                            + "A collections officer will contact you within 24 hours.",
                    action,
                    false,
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * Tier 0: Generate a payment link for immediate settlement. Pure Java, no LLM.
     */
    public CollectionsResponse handlePayNow(CollectionsRequest request) {
        long startTime = System.currentTimeMillis();
        String action = "PAYMENT_NOW";

        // Vault policy check
        PolicyDecision decision = evaluatePolicy(request.customerId(), action, request);
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault denied PAYMENT_NOW: customerId={}, reason={}", request.customerId(), decision.reason());
            publishAudit(request, action, decision, null, startTime);
            return CollectionsResponse.denied(request.sessionId(), decision.reason(), action);
        }
        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Vault escalated PAYMENT_NOW: customerId={}", request.customerId());
            publishAudit(request, action, decision, null, startTime);
            return CollectionsResponse.escalated(request.sessionId(), action);
        }

        // Generate payment link - pure Java, no LLM needed
        String paymentToken = UUID.randomUUID().toString().replace("-", "");
        String paymentLink = String.format("%s/pay?token=%s&customerId=%s&loanId=%s&amount=%s",
                paymentBaseUrl,
                paymentToken,
                request.customerId(),
                request.loanId() != null ? request.loanId() : "",
                request.overdueAmount() != null ? request.overdueAmount().toPlainString() : "0");

        String message = String.format(
                "Your payment link has been generated. Please use the following link to make your payment "
                        + "of INR %s for loan account %s. The link is valid for 24 hours.\n\nPayment Link: %s",
                request.overdueAmount() != null ? request.overdueAmount().toPlainString() : "N/A",
                request.loanId() != null ? request.loanId() : "N/A",
                paymentLink);

        // Record the interaction
        CollectionsInteraction interaction = CollectionsInteraction.builder()
                .customerId(request.customerId())
                .callTimestamp(Instant.now())
                .outcome("PAYMENT_LINK_GENERATED")
                .offerMade("Immediate payment link")
                .build();
        interactionRepository.save(interaction);

        CollectionsResponse response = new CollectionsResponse(
                request.sessionId(),
                message,
                action,
                false,
                paymentLink,
                request.overdueAmount(),
                BigDecimal.ZERO
        );

        publishAudit(request, action, decision, response.message(), startTime);
        return response;
    }

    /**
     * General collections query handled by the LLM.
     */
    private CollectionsResponse handleGeneral(CollectionsRequest request) {
        long startTime = System.currentTimeMillis();
        String action = "GENERAL";

        PolicyDecision decision = evaluatePolicy(request.customerId(), action, request);
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            publishAudit(request, action, decision, null, startTime);
            return CollectionsResponse.denied(request.sessionId(), decision.reason(), action);
        }

        try {
            String userPrompt = String.format(
                    "Customer ID: %s\nLoan ID: %s\nOverdue Amount: %s\nCustomer message: %s\n\n"
                            + "Please assist this customer with their collections query. "
                            + "Be empathetic and professional.",
                    request.customerId(),
                    request.loanId() != null ? request.loanId() : "N/A",
                    request.overdueAmount() != null ? request.overdueAmount().toPlainString() : "N/A",
                    request.message() != null ? request.message() : "");

            String llmResponse = llmRouter.chat(SYSTEM_PROMPT, userPrompt);

            CollectionsResponse response = new CollectionsResponse(
                    request.sessionId(),
                    llmResponse,
                    action,
                    false,
                    null,
                    null,
                    null
            );

            publishAudit(request, action, decision, response.message(), startTime);
            return response;

        } catch (Exception e) {
            log.error("Error processing general collections query: customerId={}", request.customerId(), e);
            publishAudit(request, action, decision, "ERROR: " + e.getMessage(), startTime);
            return new CollectionsResponse(
                    request.sessionId(),
                    "We encountered an issue processing your request. Please try again or call our helpline.",
                    action,
                    false,
                    null,
                    null,
                    null
            );
        }
    }

    // ── Helper methods ──

    private PolicyDecision evaluatePolicy(String customerId, String action, CollectionsRequest request) {
        Map<String, Object> context = new HashMap<>();
        context.put("channel", "collections");
        if (request.sessionId() != null) {
            context.put("sessionId", request.sessionId());
        }
        if (request.overdueAmount() != null) {
            context.put("overdueAmount", request.overdueAmount().toString());
        }
        if (request.loanId() != null) {
            context.put("loanId", request.loanId());
        }

        return vaultClient.evaluatePolicy(
                AGENT_ID,
                action.toLowerCase(),
                "customer:" + customerId,
                context
        );
    }

    /**
     * Increment the weekly contact count for a customer in Redis.
     * Key format: collections:contact_count:{customerId}:{yearWeek}
     * TTL: 7 days to auto-expire old counters.
     */
    void incrementContactCount(String customerId) {
        try {
            String yearWeek = getYearWeek();
            String key = CONTACT_COUNT_KEY_PREFIX + customerId + ":" + yearWeek;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, CONTACT_COUNT_TTL);
            }
            log.debug("Contact count for customerId={}, week={}: {}", customerId, yearWeek, count);
        } catch (Exception e) {
            log.warn("Failed to increment contact count in Redis for customerId={}: {}",
                    customerId, e.getMessage());
        }
    }

    /**
     * Get the current weekly contact count for a customer from Redis.
     */
    int getContactCount(String customerId) {
        try {
            String yearWeek = getYearWeek();
            String key = CONTACT_COUNT_KEY_PREFIX + customerId + ":" + yearWeek;
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("Failed to read contact count from Redis for customerId={}: {}",
                    customerId, e.getMessage());
            return 0;
        }
    }

    private String getYearWeek() {
        LocalDate now = LocalDate.now();
        int weekOfYear = now.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
        return now.getYear() + "-W" + String.format("%02d", weekOfYear);
    }

    private String buildPaymentPlanPrompt(CollectionsRequest request) {
        return String.format("""
                Customer ID: %s
                Loan ID: %s
                Overdue Amount: INR %s
                Customer message: %s

                The customer is requesting a restructured EMI payment plan for their overdue account.
                Please negotiate a feasible repayment schedule considering:
                1. The total overdue amount and any applicable late fees
                2. The customer's ability to pay (based on their message)
                3. Standard restructuring options: extended tenure, reduced EMI, moratorium period
                4. Present 2-3 options with clear EMI amounts and timelines
                5. Be empathetic but ensure the bank's interests are protected
                """,
                request.customerId(),
                request.loanId() != null ? request.loanId() : "N/A",
                request.overdueAmount() != null ? request.overdueAmount().toPlainString() : "N/A",
                request.message() != null ? request.message() : "");
    }

    private String buildSettlementPrompt(CollectionsRequest request,
                                         BigDecimal discountPct,
                                         BigDecimal settlementAmount) {
        return String.format("""
                Customer ID: %s
                Loan ID: %s
                Original Overdue Amount: INR %s
                Approved Discount: %s%%
                Settlement Amount: INR %s
                Customer message: %s

                Present this settlement offer to the customer. Explain:
                1. The original overdue amount and the discount being offered
                2. The final settlement amount to be paid
                3. The settlement must be completed within 30 days of acceptance
                4. Once settled, the account will be marked as "Settled" (not "Closed")
                5. Impact on credit score: settled status vs continued default
                6. This is a one-time offer subject to bank approval
                """,
                request.customerId(),
                request.loanId() != null ? request.loanId() : "N/A",
                request.overdueAmount() != null ? request.overdueAmount().toPlainString() : "N/A",
                discountPct.toPlainString(),
                settlementAmount.toPlainString(),
                request.message() != null ? request.message() : "");
    }

    private void publishAudit(CollectionsRequest request, String action,
                              PolicyDecision decision, String responsePayload, long startTime) {
        try {
            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    AGENT_ID,
                    instanceId,
                    request.customerId(),
                    action,
                    "customer:" + request.customerId(),
                    decision.decision().name(),
                    request.message() != null ? request.message() : "",
                    responsePayload != null ? responsePayload.substring(0, Math.min(responsePayload.length(), 500)) : "",
                    System.currentTimeMillis() - startTime
            );
            auditEventPublisher.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish audit event for customerId={}, action={}: {}",
                    request.customerId(), action, e.getMessage());
        }
    }
}
