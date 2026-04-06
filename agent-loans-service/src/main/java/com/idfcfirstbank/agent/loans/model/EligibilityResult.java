package com.idfcfirstbank.agent.loans.model;

import java.util.List;
import java.util.Map;

/**
 * Result of a loan eligibility check combining data from credit bureau,
 * account aggregator, and core banking systems.
 *
 * @param customerId    the customer identifier
 * @param eligible      whether the customer is eligible for a loan
 * @param maxAmount     the maximum eligible loan amount in INR
 * @param interestRate  the applicable interest rate (annual percentage)
 * @param tenureOptions available tenure options in months
 * @param emiAmounts    EMI amounts mapped by tenure in months
 * @param cibilScore    the customer's CIBIL credit score
 * @param reason        explanation for the eligibility decision
 */
public record EligibilityResult(
        String customerId,
        boolean eligible,
        double maxAmount,
        double interestRate,
        List<Integer> tenureOptions,
        Map<Integer, Double> emiAmounts,
        int cibilScore,
        String reason
) {
}
