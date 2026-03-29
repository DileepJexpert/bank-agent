package com.idfcfirstbank.agent.card.model;

import java.math.BigDecimal;

/**
 * Response for a reward points inquiry.
 *
 * @param points        total reward points balance
 * @param valueInRupees equivalent value in INR
 * @param expiryDate    date when the oldest batch of points expires (ISO format)
 */
public record RewardPointsResponse(
        long points,
        BigDecimal valueInRupees,
        String expiryDate
) {
}
