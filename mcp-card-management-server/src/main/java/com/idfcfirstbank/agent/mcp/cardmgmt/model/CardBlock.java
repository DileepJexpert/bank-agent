package com.idfcfirstbank.agent.mcp.cardmgmt.model;

import java.time.LocalDateTime;

/**
 * Result of a card block operation.
 *
 * @param blocked        whether the card was successfully blocked
 * @param blockReference unique reference number for the block action
 * @param blockedAt      timestamp when the card was blocked
 * @param cardLast4      last 4 digits of the blocked card (NEVER the full number)
 * @param reason         reason for blocking the card
 */
public record CardBlock(
        boolean blocked,
        String blockReference,
        LocalDateTime blockedAt,
        String cardLast4,
        String reason
) {
}
