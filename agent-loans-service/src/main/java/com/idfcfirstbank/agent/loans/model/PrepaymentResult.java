package com.idfcfirstbank.agent.loans.model;

import java.util.Map;

/**
 * Result of a loan prepayment calculation showing impact scenarios.
 *
 * @param customerId         the customer identifier
 * @param loanId             the loan identifier
 * @param currentOutstanding the current outstanding principal amount in INR
 * @param prepaymentAmount   the prepayment amount in INR
 * @param reducedEmiOption   scenario with reduced EMI keeping same tenure (keys: newEmi, savedInterest, tenure)
 * @param reducedTenureOption scenario with reduced tenure keeping same EMI (keys: emi, savedInterest, newTenure)
 * @param prepaymentCharges  any prepayment charges applicable (0 for floating rate per RBI)
 * @param floatingRate       whether the loan is on a floating interest rate
 */
public record PrepaymentResult(
        String customerId,
        String loanId,
        double currentOutstanding,
        double prepaymentAmount,
        Map<String, Object> reducedEmiOption,
        Map<String, Object> reducedTenureOption,
        double prepaymentCharges,
        boolean floatingRate
) {
}
