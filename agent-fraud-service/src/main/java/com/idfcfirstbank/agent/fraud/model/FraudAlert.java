package com.idfcfirstbank.agent.fraud.model;

import java.time.Instant;

public record FraudAlert(
        String alertId,
        String txnId,
        String customerId,
        double riskScore,
        String riskLevel,
        double mlScore,
        double ruleScore,
        double behavioralScore,
        String actionTaken,
        Instant timestamp
) {
}
