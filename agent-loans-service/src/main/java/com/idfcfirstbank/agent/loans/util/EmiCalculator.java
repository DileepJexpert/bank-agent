package com.idfcfirstbank.agent.loans.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for EMI (Equated Monthly Installment) calculations.
 * <p>
 * Uses the standard reducing-balance EMI formula:
 * EMI = P * r * (1+r)^n / ((1+r)^n - 1)
 * where P = principal, r = monthly rate, n = tenure in months.
 */
public final class EmiCalculator {

    private EmiCalculator() {
        // Utility class - prevent instantiation
    }

    /**
     * Calculate the EMI for a given principal, annual interest rate, and tenure.
     *
     * @param principal    the loan principal amount in INR
     * @param annualRate   the annual interest rate as a percentage (e.g. 8.5 for 8.5%)
     * @param tenureMonths the loan tenure in months
     * @return the monthly EMI amount, rounded to 2 decimal places
     * @throws IllegalArgumentException if any input is invalid
     */
    public static double calculateEMI(double principal, double annualRate, int tenureMonths) {
        if (principal <= 0) {
            throw new IllegalArgumentException("Principal must be positive");
        }
        if (annualRate < 0) {
            throw new IllegalArgumentException("Annual rate must be non-negative");
        }
        if (tenureMonths <= 0) {
            throw new IllegalArgumentException("Tenure must be positive");
        }

        // Zero interest rate edge case
        if (annualRate == 0) {
            return Math.round(principal / tenureMonths * 100.0) / 100.0;
        }

        double monthlyRate = annualRate / 12.0 / 100.0;
        double power = Math.pow(1 + monthlyRate, tenureMonths);
        double emi = principal * monthlyRate * power / (power - 1);

        return Math.round(emi * 100.0) / 100.0;
    }

    /**
     * Calculate the impact of a prepayment on a loan.
     * <p>
     * Returns two scenarios:
     * <ul>
     *   <li>Reduced EMI: keep the same tenure, compute new lower EMI</li>
     *   <li>Reduced Tenure: keep the same EMI, compute new shorter tenure</li>
     * </ul>
     *
     * @param outstanding     the current outstanding principal
     * @param prepayAmount    the prepayment amount
     * @param annualRate      the annual interest rate as a percentage
     * @param remainingMonths the remaining tenure in months
     * @return a map with keys: newEMI, savedInterest, tenureReduction, newTenure, originalEMI
     * @throws IllegalArgumentException if prepayment exceeds outstanding
     */
    public static Map<String, Object> prepaymentImpact(double outstanding, double prepayAmount,
                                                        double annualRate, int remainingMonths) {
        if (prepayAmount <= 0) {
            throw new IllegalArgumentException("Prepayment amount must be positive");
        }
        if (prepayAmount > outstanding) {
            throw new IllegalArgumentException("Prepayment amount cannot exceed outstanding principal");
        }
        if (outstanding <= 0) {
            throw new IllegalArgumentException("Outstanding amount must be positive");
        }
        if (remainingMonths <= 0) {
            throw new IllegalArgumentException("Remaining months must be positive");
        }

        double originalEmi = calculateEMI(outstanding, annualRate, remainingMonths);
        double newPrincipal = outstanding - prepayAmount;

        // Scenario 1: Reduced EMI (same tenure)
        double newEmi = calculateEMI(newPrincipal, annualRate, remainingMonths);
        double originalTotalPayment = originalEmi * remainingMonths;
        double newTotalPayment = newEmi * remainingMonths + prepayAmount;
        double savedInterestReducedEmi = originalTotalPayment - newTotalPayment;

        // Scenario 2: Reduced Tenure (same EMI)
        int newTenure = calculateTenureForEmi(newPrincipal, annualRate, originalEmi);
        double newTotalPaymentReducedTenure = originalEmi * newTenure + prepayAmount;
        double savedInterestReducedTenure = originalTotalPayment - newTotalPaymentReducedTenure;
        int tenureReduction = remainingMonths - newTenure;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalEMI", originalEmi);
        result.put("newEMI", newEmi);
        result.put("savedInterest", Math.round(savedInterestReducedEmi * 100.0) / 100.0);
        result.put("newTenure", newTenure);
        result.put("tenureReduction", tenureReduction);
        result.put("savedInterestReducedTenure", Math.round(savedInterestReducedTenure * 100.0) / 100.0);

        return result;
    }

    /**
     * Generate a full amortization schedule for a loan.
     *
     * @param principal    the loan principal amount
     * @param annualRate   the annual interest rate as a percentage
     * @param tenureMonths the loan tenure in months
     * @return a list of maps, each representing one month's breakdown
     */
    public static List<Map<String, Object>> generateAmortizationSchedule(double principal,
                                                                          double annualRate,
                                                                          int tenureMonths) {
        if (principal <= 0 || tenureMonths <= 0) {
            throw new IllegalArgumentException("Principal and tenure must be positive");
        }

        double emi = calculateEMI(principal, annualRate, tenureMonths);
        double monthlyRate = annualRate / 12.0 / 100.0;
        double balance = principal;

        List<Map<String, Object>> schedule = new ArrayList<>(tenureMonths);

        for (int month = 1; month <= tenureMonths; month++) {
            double interestComponent = Math.round(balance * monthlyRate * 100.0) / 100.0;
            double principalComponent = Math.round((emi - interestComponent) * 100.0) / 100.0;

            // Adjust the last EMI to clear the balance exactly
            if (month == tenureMonths) {
                principalComponent = Math.round(balance * 100.0) / 100.0;
                double lastEmi = principalComponent + interestComponent;
                balance = 0;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("month", month);
                row.put("emi", lastEmi);
                row.put("principalComponent", principalComponent);
                row.put("interestComponent", interestComponent);
                row.put("outstandingBalance", 0.0);
                schedule.add(row);
            } else {
                balance = Math.round((balance - principalComponent) * 100.0) / 100.0;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("month", month);
                row.put("emi", emi);
                row.put("principalComponent", principalComponent);
                row.put("interestComponent", interestComponent);
                row.put("outstandingBalance", balance);
                schedule.add(row);
            }
        }

        return schedule;
    }

    /**
     * Calculate the number of months required to repay a loan at a given EMI.
     */
    private static int calculateTenureForEmi(double principal, double annualRate, double emi) {
        if (annualRate == 0) {
            return (int) Math.ceil(principal / emi);
        }

        double monthlyRate = annualRate / 12.0 / 100.0;

        // EMI must be greater than monthly interest to ever pay off the loan
        double monthlyInterest = principal * monthlyRate;
        if (emi <= monthlyInterest) {
            // EMI too small to cover interest - return a large number
            return Integer.MAX_VALUE;
        }

        // n = -log(1 - P*r/EMI) / log(1+r)
        double n = -Math.log(1 - (principal * monthlyRate / emi)) / Math.log(1 + monthlyRate);
        return (int) Math.ceil(n);
    }
}
