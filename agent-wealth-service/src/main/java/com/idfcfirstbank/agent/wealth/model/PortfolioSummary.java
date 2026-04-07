package com.idfcfirstbank.agent.wealth.model;

import java.util.List;
import java.util.Map;

/**
 * Aggregated portfolio summary for a customer across all wealth products.
 *
 * @param customerId  the customer identifier
 * @param mutualFunds list of mutual fund holdings with details
 * @param fixedDeposits list of fixed deposit holdings with details
 * @param insurance   list of insurance policies with details
 * @param totalValue  total portfolio value in INR
 * @param asOfDate    the date as of which the portfolio was valued
 */
public record PortfolioSummary(
        String customerId,
        List<Map<String, Object>> mutualFunds,
        List<Map<String, Object>> fixedDeposits,
        List<Map<String, Object>> insurance,
        double totalValue,
        String asOfDate
) {
}
