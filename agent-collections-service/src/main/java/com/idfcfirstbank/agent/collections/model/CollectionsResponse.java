package com.idfcfirstbank.agent.collections.model;

import java.math.BigDecimal;

/**
 * Response from the Collections Agent.
 *
 * @param sessionId        session identifier for conversation continuity
 * @param message          agent response message to display to the customer
 * @param intent           the resolved intent that was processed
 * @param escalated        true if the request was escalated to a human agent
 * @param paymentLink      payment link for immediate settlement (PAYMENT_NOW intent)
 * @param settlementAmount calculated settlement amount after applicable discount
 * @param discountPercent  discount percentage applied to the settlement
 */
public record CollectionsResponse(
        String sessionId,
        String message,
        String intent,
        boolean escalated,
        String paymentLink,
        BigDecimal settlementAmount,
        BigDecimal discountPercent
) {

    /**
     * Factory method for a denied response.
     */
    public static CollectionsResponse denied(String sessionId, String reason, String intent) {
        return new CollectionsResponse(
                sessionId,
                "Unable to process your request: " + reason,
                intent,
                false,
                null,
                null,
                null
        );
    }

    /**
     * Factory method for an escalated response.
     */
    public static CollectionsResponse escalated(String sessionId, String intent) {
        return new CollectionsResponse(
                sessionId,
                "Your request requires review by a senior collections officer. "
                        + "You will be contacted within 24 hours.",
                intent,
                true,
                null,
                null,
                null
        );
    }
}
