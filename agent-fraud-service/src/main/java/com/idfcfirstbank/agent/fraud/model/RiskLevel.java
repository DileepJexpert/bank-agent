package com.idfcfirstbank.agent.fraud.model;

public enum RiskLevel {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * Determines the risk level from a numeric score between 0 and 1.
     *
     * @param score a value between 0.0 and 1.0
     * @return the corresponding RiskLevel
     */
    public static RiskLevel fromScore(double score) {
        if (score >= 0.9) {
            return CRITICAL;
        } else if (score >= 0.7) {
            return HIGH;
        } else if (score >= 0.4) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }
}
