package com.idfcfirstbank.agent.orchestrator.model;

import java.util.Map;

/**
 * Result of intent detection on a customer message.
 *
 * @param intent     the recognised intent identifier (e.g. BALANCE_INQUIRY, FUND_TRANSFER)
 * @param confidence confidence score between 0.0 and 1.0
 * @param tier       the detection tier that resolved this intent (0 = keyword, 1 = small model, 2 = large LLM)
 * @param parameters extracted parameters from the message (e.g. account number, amount)
 */
public record DetectedIntent(
        String intent,
        double confidence,
        int tier,
        Map<String, String> parameters
) {
}
