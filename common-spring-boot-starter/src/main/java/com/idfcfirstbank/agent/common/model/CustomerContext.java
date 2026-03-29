package com.idfcfirstbank.agent.common.model;

import java.util.List;

/**
 * Customer context carrying profile and interaction data across agent boundaries.
 *
 * @param customerId         unique customer identifier
 * @param name               customer's full name
 * @param segment            customer segment (e.g. "RETAIL", "PREMIUM", "HNI", "NRI")
 * @param riskProfile        risk classification (e.g. "LOW", "MEDIUM", "HIGH")
 * @param preferredLanguage  preferred language code (e.g. "en", "hi", "mr")
 * @param interactionHistory list of recent interaction summaries for context continuity
 */
public record CustomerContext(
        String customerId,
        String name,
        String segment,
        String riskProfile,
        String preferredLanguage,
        List<String> interactionHistory
) {
}
