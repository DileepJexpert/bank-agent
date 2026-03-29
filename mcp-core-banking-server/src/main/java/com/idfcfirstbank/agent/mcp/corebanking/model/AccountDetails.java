package com.idfcfirstbank.agent.mcp.corebanking.model;

import java.time.LocalDateTime;

/**
 * Comprehensive account details from the core banking system.
 *
 * @param accountNumber  the account number
 * @param customerId     the customer identifier
 * @param customerName   the account holder's name
 * @param accountType    account type (SAVINGS, CURRENT, etc.)
 * @param status         account status (ACTIVE, DORMANT, CLOSED)
 * @param currency       currency code
 * @param bankName       name of the bank
 * @param branchName     branch name and location
 * @param ifscCode       IFSC code of the branch
 * @param openedDate     date the account was opened
 * @param email          registered email address
 * @param phone          registered phone number (partially masked)
 */
public record AccountDetails(
        String accountNumber,
        String customerId,
        String customerName,
        String accountType,
        String status,
        String currency,
        String bankName,
        String branchName,
        String ifscCode,
        LocalDateTime openedDate,
        String email,
        String phone
) {
}
