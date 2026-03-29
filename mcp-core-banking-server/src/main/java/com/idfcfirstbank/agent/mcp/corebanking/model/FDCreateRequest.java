package com.idfcfirstbank.agent.mcp.corebanking.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request to create a new fixed deposit.
 *
 * @param customerId    the customer identifier
 * @param accountNumber the source account to debit
 * @param amount        FD principal amount
 * @param tenureMonths  tenure in months
 * @param currency      currency code
 */
public record FDCreateRequest(
        @NotBlank String customerId,
        @NotBlank String accountNumber,
        @Positive BigDecimal amount,
        @Positive int tenureMonths,
        String currency
) {
    public FDCreateRequest {
        if (currency == null || currency.isBlank()) currency = "INR";
    }

    /**
     * Response after creating a fixed deposit.
     *
     * @param fdNumber       unique FD identifier
     * @param status         FD status (ACTIVE, FAILED)
     * @param principal      principal amount
     * @param interestRate   annual interest rate (percentage)
     * @param tenureMonths   tenure in months
     * @param startDate      FD start date
     * @param maturityDate   FD maturity date
     * @param maturityAmount estimated maturity amount
     */
    public record FDCreateResponse(
            String fdNumber,
            String status,
            BigDecimal principal,
            BigDecimal interestRate,
            int tenureMonths,
            LocalDateTime startDate,
            LocalDateTime maturityDate,
            BigDecimal maturityAmount
    ) {
    }
}
