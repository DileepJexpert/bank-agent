package com.idfcfirstbank.agent.mcp.corebanking.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request to initiate a fund transfer.
 *
 * @param customerId  the customer initiating the transfer
 * @param fromAccount source account number
 * @param toAccount   destination account number
 * @param amount      transfer amount
 * @param currency    currency code
 * @param mode        transfer mode (NEFT, RTGS, IMPS, UPI)
 * @param remarks     optional narration / remarks
 */
public record TransferRequest(
        @NotBlank String customerId,
        @NotBlank String fromAccount,
        @NotBlank String toAccount,
        @Positive BigDecimal amount,
        String currency,
        String mode,
        String remarks
) {
    public TransferRequest {
        if (currency == null || currency.isBlank()) currency = "INR";
        if (mode == null || mode.isBlank()) mode = "IMPS";
    }

    /**
     * Response after initiating a fund transfer.
     *
     * @param referenceNumber unique transfer reference
     * @param status          transfer status (SUCCESS, PENDING, FAILED)
     * @param message         descriptive message
     * @param timestamp       when the transfer was processed
     */
    public record TransferResponse(
            String referenceNumber,
            String status,
            String message,
            LocalDateTime timestamp
    ) {
    }
}
