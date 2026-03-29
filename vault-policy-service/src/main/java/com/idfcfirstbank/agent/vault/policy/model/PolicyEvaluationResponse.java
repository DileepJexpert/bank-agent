package com.idfcfirstbank.agent.vault.policy.model;

public record PolicyEvaluationResponse(
        Decision decision,
        String reason,
        String policyRef,
        long evaluationTimeMs
) {

    public enum Decision {
        ALLOW,
        DENY,
        ESCALATE
    }
}
