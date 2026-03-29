package com.idfcfirstbank.agent.card.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request to raise a card transaction dispute.
 *
 * @param customerId    the authenticated customer identifier
 * @param transactionId the transaction to dispute
 * @param amount        the disputed amount
 * @param merchantName  the merchant name associated with the transaction
 * @param reason        reason for the dispute
 * @param date          date of the original transaction (ISO format)
 */
public record DisputeRequest(
        @NotBlank(message = "customerId is required") String customerId,
        @NotBlank(message = "transactionId is required") String transactionId,
        @Positive(message = "amount must be positive") BigDecimal amount,
        String merchantName,
        @NotBlank(message = "reason is required") String reason,
        String date
) {
    public DisputeRequest {
        if (merchantName == null) merchantName = "Unknown";
        if (date == null) date = "";
    }
}
