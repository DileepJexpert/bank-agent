package com.idfcfirstbank.agent.mcp.corebanking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transaction history for an account.
 *
 * @param accountNumber the account number
 * @param customerId    the customer identifier
 * @param transactions  list of transactions
 * @param totalCount    total number of transactions in the result
 */
public record TransactionHistory(
        String accountNumber,
        String customerId,
        List<Transaction> transactions,
        int totalCount
) {

    /**
     * A single transaction entry.
     *
     * @param transactionId   unique transaction identifier
     * @param date            date and time of the transaction
     * @param description     narration / description
     * @param amount          transaction amount
     * @param type            CREDIT or DEBIT
     * @param runningBalance  balance after this transaction
     * @param referenceNumber external reference number
     */
    public record Transaction(
            String transactionId,
            LocalDateTime date,
            String description,
            BigDecimal amount,
            String type,
            BigDecimal runningBalance,
            String referenceNumber
    ) {
    }
}
