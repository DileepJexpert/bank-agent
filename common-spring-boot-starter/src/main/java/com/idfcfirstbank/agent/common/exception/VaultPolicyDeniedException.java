package com.idfcfirstbank.agent.common.exception;

import lombok.Getter;

/**
 * Thrown when the Vault Policy Service denies an agent action.
 */
@Getter
public class VaultPolicyDeniedException extends RuntimeException {

    private final String policyRef;
    private final String reason;

    public VaultPolicyDeniedException(String policyRef, String reason) {
        super("Policy denied [%s]: %s".formatted(policyRef, reason));
        this.policyRef = policyRef;
        this.reason = reason;
    }

    public VaultPolicyDeniedException(String policyRef, String reason, Throwable cause) {
        super("Policy denied [%s]: %s".formatted(policyRef, reason), cause);
        this.policyRef = policyRef;
        this.reason = reason;
    }
}
