package com.idfcfirstbank.agent.loans.model;

/**
 * Response from the Loan Agent for a processed query.
 *
 * @param sessionId        the conversation session identifier
 * @param message          the response message text
 * @param intent           the intent that was processed
 * @param escalated        whether the request was escalated for human review
 * @param requiresApproval whether the loan action requires manager approval
 */
public record LoanResponse(
        String sessionId,
        String message,
        String intent,
        boolean escalated,
        boolean requiresApproval
) {
}
