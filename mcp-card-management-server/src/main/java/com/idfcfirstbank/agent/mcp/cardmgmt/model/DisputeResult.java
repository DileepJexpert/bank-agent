package com.idfcfirstbank.agent.mcp.cardmgmt.model;

import java.math.BigDecimal;

/**
 * Result of a dispute raising operation.
 *
 * @param disputeId              unique dispute identifier
 * @param status                 dispute status (REGISTERED, FAILED, UNDER_REVIEW)
 * @param expectedResolutionDate expected date of resolution (ISO format)
 * @param transactionId          the transaction that was disputed
 * @param disputedAmount         the disputed amount
 */
public record DisputeResult(
        String disputeId,
        String status,
        String expectedResolutionDate,
        String transactionId,
        BigDecimal disputedAmount
) {
}
