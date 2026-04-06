package com.idfcfirstbank.agent.fraud.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.fraud.entity.CustomerProfile;
import com.idfcfirstbank.agent.fraud.model.FraudAlert;
import com.idfcfirstbank.agent.fraud.model.RiskLevel;
import com.idfcfirstbank.agent.fraud.model.TransactionEvent;
import com.idfcfirstbank.agent.fraud.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskScoringProcessor {

    private static final Set<String> HIGH_RISK_CATEGORIES = Set.of("gambling", "crypto", "forex");
    private static final double ML_WEIGHT = 0.4;
    private static final double RULES_WEIGHT = 0.3;
    private static final double BEHAVIORAL_WEIGHT = 0.3;

    private final CustomerProfileService customerProfileService;
    private final ObjectMapper objectMapper;

    /**
     * ML-based heuristic scoring. Returns a score between 0 and 1.
     */
    public double scoreMl(TransactionEvent txn, CustomerProfile profile) {
        double score = 0.0;

        // High amount transactions
        if (txn.amount() > 50000) {
            score += 0.15;
        }

        // UPI channel with high amount
        if ("UPI".equalsIgnoreCase(txn.channel()) && txn.amount() > 25000) {
            score += 0.1;
        }

        // New device detection
        if (txn.deviceId() != null && profile != null) {
            List<String> knownDevices = parseJsonList(profile.getCommonDevices());
            if (!knownDevices.contains(txn.deviceId())) {
                score += 0.2;
            }
        }

        return Math.min(score, 1.0);
    }

    /**
     * Rule-based scoring. Returns a score between 0 and 1.
     */
    public double scoreRules(TransactionEvent txn) {
        double score = 0.0;

        // Late-night transactions (00:00 - 05:00)
        if (txn.timestamp() != null) {
            int hour = txn.timestamp().atZone(ZoneOffset.UTC).getHour();
            if (hour >= 0 && hour < 5) {
                score += 0.15;
            }
        }

        // High-risk merchant category
        if (txn.merchantCategory() != null && HIGH_RISK_CATEGORIES.contains(txn.merchantCategory().toLowerCase())) {
            score += 0.2;
        }

        // Location anomaly: simplified distance check using geo-location string comparison
        // In production, this would use actual geo-coordinate distance calculation
        if (txn.geoLocation() != null) {
            score += scoreLocationAnomaly(txn);
        }

        return Math.min(score, 1.0);
    }

    /**
     * Behavioral scoring based on customer profile deviations. Returns a score between 0 and 1.
     */
    public double scoreBehavioral(TransactionEvent txn, CustomerProfile profile) {
        double score = 0.0;

        if (profile == null) {
            // No profile means first-time customer; slightly elevated risk
            return 0.1;
        }

        // Amount deviation: more than 2x the customer average
        if (profile.getAvgAmount() > 0 && txn.amount() > 2 * profile.getAvgAmount()) {
            score += 0.2;
        }

        // Velocity check: if average daily count exists and would be exceeded
        if (profile.getAvgDailyCount() > 0) {
            // Simplified: if the existing average suggests low activity but this is a burst,
            // we flag it. In production, we would track today's actual count in Redis.
            double threshold = 3 * profile.getAvgDailyCount();
            // Using avgDailyCount as a proxy; real implementation would check Redis counter
            if (profile.getAvgDailyCount() > threshold) {
                score += 0.15;
            }
        }

        return Math.min(score, 1.0);
    }

    /**
     * Computes the overall risk for a transaction by combining ML, rule-based, and behavioral scores.
     */
    public FraudAlert computeRisk(TransactionEvent txn) {
        CustomerProfile profile = customerProfileService.getProfile(txn.customerId()).orElse(null);

        double mlScore = scoreMl(txn, profile);
        double ruleScore = scoreRules(txn);
        double behavioralScore = scoreBehavioral(txn, profile);

        double compositeScore = (ML_WEIGHT * mlScore) + (RULES_WEIGHT * ruleScore) + (BEHAVIORAL_WEIGHT * behavioralScore);
        compositeScore = Math.min(compositeScore, 1.0);

        RiskLevel riskLevel = RiskLevel.fromScore(compositeScore);

        String actionTaken = determineAction(riskLevel);

        log.info("Risk computed for txn={}: ml={}, rules={}, behavioral={}, composite={}, level={}",
                txn.txnId(), mlScore, ruleScore, behavioralScore, compositeScore, riskLevel);

        return new FraudAlert(
                UUID.randomUUID().toString(),
                txn.txnId(),
                txn.customerId(),
                compositeScore,
                riskLevel.name(),
                mlScore,
                ruleScore,
                behavioralScore,
                actionTaken,
                Instant.now()
        );
    }

    private String determineAction(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "LOG_ONLY";
            case MEDIUM -> "LOG_AND_NOTIFY";
            case HIGH -> "BLOCK_AND_FREEZE";
            case CRITICAL -> "BLOCK_FREEZE_AND_ESCALATE";
        };
    }

    private double scoreLocationAnomaly(TransactionEvent txn) {
        // In production, this would compare lat/lng coordinates with the customer's
        // common locations and compute actual distance. For now, we check if the
        // location is in the customer's known locations.
        CustomerProfile profile = customerProfileService.getProfile(txn.customerId()).orElse(null);
        if (profile == null) {
            return 0.0;
        }

        List<String> knownLocations = parseJsonList(profile.getCommonLocations());
        if (!knownLocations.isEmpty() && !knownLocations.contains(txn.geoLocation())) {
            return 0.25;
        }

        return 0.0;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", json, e);
            return Collections.emptyList();
        }
    }
}
