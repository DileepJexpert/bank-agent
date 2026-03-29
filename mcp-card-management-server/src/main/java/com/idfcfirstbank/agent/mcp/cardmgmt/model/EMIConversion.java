package com.idfcfirstbank.agent.mcp.cardmgmt.model;

import java.math.BigDecimal;

/**
 * Result of an EMI conversion operation.
 *
 * @param emiAmount     monthly EMI amount in INR
 * @param interestRate  annual interest rate (percentage)
 * @param totalCost     total cost including interest
 * @param emiId         unique EMI plan identifier
 * @param tenureMonths  EMI tenure in months
 * @param transactionId the original transaction that was converted
 */
public record EMIConversion(
        BigDecimal emiAmount,
        BigDecimal interestRate,
        BigDecimal totalCost,
        String emiId,
        int tenureMonths,
        String transactionId
) {
}
