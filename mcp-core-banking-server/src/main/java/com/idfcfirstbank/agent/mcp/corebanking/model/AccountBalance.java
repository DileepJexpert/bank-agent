package com.idfcfirstbank.agent.mcp.corebanking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account balance information returned by the core banking system.
 *
 * @param accountNumber   the account number
 * @param customerId      the customer identifier
 * @param accountType     account type (SAVINGS, CURRENT, etc.)
 * @param currentBalance  the current ledger balance
 * @param availableBalance the available balance (after holds/blocks)
 * @param currency        currency code (e.g. INR)
 * @param asOf            timestamp of the balance snapshot
 */
public record AccountBalance(
        String accountNumber,
        String customerId,
        String accountType,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        String currency,
        LocalDateTime asOf
) {
}
