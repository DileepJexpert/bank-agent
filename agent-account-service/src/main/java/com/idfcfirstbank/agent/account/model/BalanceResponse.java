package com.idfcfirstbank.agent.account.model;

/**
 * Response for a direct balance inquiry.
 *
 * @param customerId    the customer identifier
 * @param accountNumber the account number queried
 * @param balance       the balance information (formatted string or JSON)
 * @param success       whether the inquiry was successful
 */
public record BalanceResponse(
        String customerId,
        String accountNumber,
        String balance,
        boolean success
) {
}
