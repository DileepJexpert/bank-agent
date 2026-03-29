package com.idfcfirstbank.agent.orchestrator.routing;

import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements tiered routing for intent detection:
 * <ul>
 *   <li><b>Tier 0</b> - Java rule engine using keyword/regex matching. Zero latency, no LLM cost.
 *       Handles: balance inquiry, mini statement, branch locator, cheque status.</li>
 *   <li><b>Tier 1</b> - Small/fast model for moderately complex queries.</li>
 *   <li><b>Tier 2</b> - Large LLM for complex, multi-intent, or ambiguous queries.</li>
 *   <li><b>Tier 3</b> - Human escalation for sensitive or unresolvable queries.</li>
 * </ul>
 */
@Slf4j
@Component
public class TierRouter {

    /**
     * Keyword patterns for Tier 0 detection.
     * Each entry maps a compiled regex to the corresponding intent identifier.
     */
    private static final List<Tier0Rule> TIER0_RULES = List.of(
            // Balance inquiry
            new Tier0Rule(
                    Pattern.compile("\\b(balance|bal|account\\s*balance|check\\s*balance|available\\s*balance|"
                            + "how\\s*much|what.*balance|show.*balance)\\b", Pattern.CASE_INSENSITIVE),
                    "BALANCE_INQUIRY", 0.95
            ),
            // Mini statement
            new Tier0Rule(
                    Pattern.compile("\\b(mini\\s*statement|recent\\s*transactions?|last\\s*\\d+\\s*transactions?|"
                            + "transaction\\s*history|statement|recent\\s*activity)\\b", Pattern.CASE_INSENSITIVE),
                    "MINI_STATEMENT", 0.90
            ),
            // Cheque status
            new Tier0Rule(
                    Pattern.compile("\\b(cheque\\s*status|check\\s*status|cheque\\s*clear|"
                            + "cheque.*cleared|has.*cheque)\\b", Pattern.CASE_INSENSITIVE),
                    "CHEQUE_STATUS", 0.90
            ),
            // Cheque book request
            new Tier0Rule(
                    Pattern.compile("\\b(cheque\\s*book|check\\s*book|new\\s*cheque|request\\s*cheque|"
                            + "order\\s*cheque)\\b", Pattern.CASE_INSENSITIVE),
                    "CHEQUE_BOOK_REQUEST", 0.92
            ),
            // Fund transfer
            new Tier0Rule(
                    Pattern.compile("\\b(transfer|send\\s*money|pay\\s*\\w+|neft|rtgs|imps|upi|"
                            + "fund\\s*transfer)\\b", Pattern.CASE_INSENSITIVE),
                    "FUND_TRANSFER", 0.85
            ),
            // Fixed deposit
            new Tier0Rule(
                    Pattern.compile("\\b(fixed\\s*deposit|fd|create\\s*fd|open\\s*fd|fd\\s*rates?|"
                            + "term\\s*deposit)\\b", Pattern.CASE_INSENSITIVE),
                    "FD_CREATION", 0.88
            ),
            // Account details
            new Tier0Rule(
                    Pattern.compile("\\b(account\\s*details?|account\\s*info|account\\s*number|"
                            + "ifsc|branch\\s*details?)\\b", Pattern.CASE_INSENSITIVE),
                    "ACCOUNT_DETAILS", 0.88
            ),
            // Card block
            new Tier0Rule(
                    Pattern.compile("\\b(block\\s*(my\\s*)?card|card\\s*block|lost\\s*card|"
                            + "stolen\\s*card|freeze\\s*card)\\b", Pattern.CASE_INSENSITIVE),
                    "CARD_BLOCK", 0.95
            ),
            // Card limit
            new Tier0Rule(
                    Pattern.compile("\\b(card\\s*limit|increase\\s*limit|change\\s*limit|"
                            + "credit\\s*limit|spending\\s*limit)\\b", Pattern.CASE_INSENSITIVE),
                    "CARD_LIMIT", 0.88
            ),
            // Loan inquiry
            new Tier0Rule(
                    Pattern.compile("\\b(loan|home\\s*loan|personal\\s*loan|car\\s*loan|"
                            + "loan\\s*eligib|loan\\s*rate|emi)\\b", Pattern.CASE_INSENSITIVE),
                    "LOAN_INQUIRY", 0.82
            ),
            // Complaint
            new Tier0Rule(
                    Pattern.compile("\\b(complaint|complain|not\\s*happy|dissatisfied|problem|issue|"
                            + "escalate|speak.*manager|talk.*human)\\b", Pattern.CASE_INSENSITIVE),
                    "COMPLAINT", 0.80
            )
    );

    /**
     * Attempt Tier 0 (keyword-based) intent detection. Returns empty if no rule matches.
     *
     * @param message the customer message
     * @return detected intent if a keyword rule matches, otherwise empty
     */
    public Optional<DetectedIntent> attemptTier0Detection(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        DetectedIntent bestMatch = null;
        double bestConfidence = 0.0;

        for (Tier0Rule rule : TIER0_RULES) {
            Matcher matcher = rule.pattern().matcher(message);
            if (matcher.find()) {
                if (rule.confidence() > bestConfidence) {
                    bestConfidence = rule.confidence();
                    bestMatch = new DetectedIntent(
                            rule.intent(),
                            rule.confidence(),
                            0,  // Tier 0
                            extractSimpleParameters(message, rule.intent())
                    );
                }
            }
        }

        if (bestMatch != null) {
            log.info("Tier 0 detection: intent={}, confidence={}", bestMatch.intent(), bestMatch.confidence());
        }

        return Optional.ofNullable(bestMatch);
    }

    /**
     * Determine the appropriate tier for a given message complexity.
     *
     * @param message          the customer message
     * @param previousAttempts number of previous detection attempts
     * @return the recommended tier (0-3)
     */
    public int determineTier(String message, int previousAttempts) {
        if (previousAttempts == 0 && attemptTier0Detection(message).isPresent()) {
            return 0;
        }
        // Short messages or single-intent likely: tier 1 (fast model)
        if (message.split("\\s+").length <= 15) {
            return 1;
        }
        // Longer or complex messages: tier 2 (full LLM)
        return 2;
    }

    /**
     * Extract simple parameters from messages for Tier 0 intents.
     */
    private Map<String, String> extractSimpleParameters(String message, String intent) {
        Map<String, String> params = new HashMap<>();

        // Try to extract account numbers (10-18 digit patterns)
        Pattern accountPattern = Pattern.compile("\\b(\\d{10,18})\\b");
        Matcher accountMatcher = accountPattern.matcher(message);
        if (accountMatcher.find()) {
            params.put("accountNumber", accountMatcher.group(1));
        }

        // Try to extract amounts
        Pattern amountPattern = Pattern.compile("(?:Rs\\.?|INR|\\u20B9)\\s*(\\d+[,\\d]*\\.?\\d*)", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(message);
        if (amountMatcher.find()) {
            params.put("amount", amountMatcher.group(1).replace(",", ""));
        }

        return params;
    }

    /**
     * A Tier 0 detection rule: a regex pattern, the intent it maps to, and a confidence score.
     */
    private record Tier0Rule(Pattern pattern, String intent, double confidence) {
    }
}
