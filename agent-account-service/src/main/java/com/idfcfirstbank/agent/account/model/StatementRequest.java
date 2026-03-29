package com.idfcfirstbank.agent.account.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for an account statement over a date range.
 *
 * @param customerId    the authenticated customer identifier
 * @param accountNumber the account number
 * @param fromDate      start date (ISO format, e.g. 2025-01-01)
 * @param toDate        end date (ISO format, e.g. 2025-03-31)
 */
public record StatementRequest(
        @NotBlank(message = "customerId is required") String customerId,
        String accountNumber,
        String fromDate,
        String toDate
) {
}
