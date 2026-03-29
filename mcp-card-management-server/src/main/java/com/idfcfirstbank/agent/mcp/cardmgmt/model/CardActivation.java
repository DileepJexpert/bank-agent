package com.idfcfirstbank.agent.mcp.cardmgmt.model;

import java.time.LocalDateTime;

/**
 * Result of a card activation operation.
 *
 * @param status      activation status (ACTIVATED, FAILED, ALREADY_ACTIVE)
 * @param activatedAt timestamp when the card was activated
 * @param cardLast4   last 4 digits of the activated card (NEVER the full number)
 */
public record CardActivation(
        String status,
        LocalDateTime activatedAt,
        String cardLast4
) {
}
