package com.idfcfirstbank.agent.common.vault;

/**
 * Result of a policy evaluation performed by the Vault Policy Service.
 *
 * @param decision  the overall decision (ALLOW, DENY, or ESCALATE)
 * @param reason    human-readable explanation of why the decision was made
 * @param policyRef unique reference to the policy rule that produced this decision
 */
public record PolicyDecision(
        Decision decision,
        String reason,
        String policyRef
) {

    /**
     * Possible outcomes of a vault policy evaluation.
     */
    public enum Decision {
        /** The requested action is permitted. */
        ALLOW,
        /** The requested action is denied outright. */
        DENY,
        /** The action requires human review / escalation before proceeding. */
        ESCALATE
    }
}
