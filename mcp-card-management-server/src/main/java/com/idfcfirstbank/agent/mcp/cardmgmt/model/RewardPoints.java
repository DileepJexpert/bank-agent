package com.idfcfirstbank.agent.mcp.cardmgmt.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reward points information for a customer's card.
 *
 * @param totalPoints    total reward points balance
 * @param valueInRupees  equivalent value in INR
 * @param expiryDate     date when the oldest batch of points expires (ISO format)
 * @param recentEarnings list of recent point-earning transactions
 */
public record RewardPoints(
        long totalPoints,
        BigDecimal valueInRupees,
        String expiryDate,
        List<RecentEarning> recentEarnings
) {

    /**
     * A single reward points earning entry.
     *
     * @param description description of the earning transaction
     * @param points      points earned
     * @param date        date of the earning (ISO format)
     */
    public record RecentEarning(
            String description,
            int points,
            String date
    ) {
    }
}
