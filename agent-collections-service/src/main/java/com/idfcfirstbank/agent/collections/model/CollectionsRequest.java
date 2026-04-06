package com.idfcfirstbank.agent.collections.model;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Inbound request for the Collections Agent.
 *
 * @param sessionId    unique session identifier for conversation tracking
 * @param customerId   the customer whose account is overdue
 * @param message      the customer's message or agent-generated prompt
 * @param intent       detected intent (PAYMENT_PLAN, SETTLEMENT_OFFER, PAYMENT_NOW, GENERAL)
 * @param confidence   confidence score for the detected intent (0.0 to 1.0)
 * @param loanId       the loan account identifier
 * @param overdueAmount the total overdue amount on the account
 * @param parameters   additional contextual parameters (e.g., daysOverdue, productType)
 */
public record CollectionsRequest(
        String sessionId,
        @NotBlank(message = "customerId is required") String customerId,
        String message,
        String intent,
        Double confidence,
        String loanId,
        BigDecimal overdueAmount,
        Map<String, Object> parameters
) {
}
