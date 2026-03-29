package com.idfcfirstbank.agent.account.model;

/**
 * Response from the Account Agent for a processed query.
 *
 * @param sessionId the conversation session identifier
 * @param message   the response message text
 * @param intent    the intent that was processed
 * @param escalated whether the request was escalated for human review
 */
public record AccountQueryResponse(
        String sessionId,
        String message,
        String intent,
        boolean escalated
) {
}
