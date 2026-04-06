package com.idfcfirstbank.agent.wealth.model;

/**
 * Response from the Wealth Agent for a processed query.
 *
 * @param sessionId  the conversation session identifier
 * @param message    the response message text
 * @param intent     the intent that was processed
 * @param escalated  whether the request was escalated for human review
 * @param disclaimer regulatory disclaimer text included with investment responses
 */
public record WealthResponse(
        String sessionId,
        String message,
        String intent,
        boolean escalated,
        String disclaimer
) {
}
