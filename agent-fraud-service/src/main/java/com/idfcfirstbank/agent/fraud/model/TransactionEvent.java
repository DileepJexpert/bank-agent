package com.idfcfirstbank.agent.fraud.model;

import java.time.Instant;

public record TransactionEvent(
        String txnId,
        String customerId,
        double amount,
        String currency,
        String merchantId,
        String merchantCategory,
        String channel,
        String deviceId,
        String ipAddress,
        String geoLocation,
        Instant timestamp
) {
}
