package com.idfcfirstbank.agent.account.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single transaction record in account history.
 *
 * @param transactionId   unique transaction identifier
 * @param date            date and time of the transaction
 * @param description     transaction description / narration
 * @param amount          transaction amount
 * @param type            transaction type (CREDIT or DEBIT)
 * @param runningBalance  balance after this transaction
 * @param referenceNumber external reference number
 */
public record TransactionRecord(
        String transactionId,
        LocalDateTime date,
        String description,
        BigDecimal amount,
        String type,
        BigDecimal runningBalance,
        String referenceNumber
) {
}
