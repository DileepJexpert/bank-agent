package com.idfcfirstbank.agent.orchestrator.service;

import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import com.idfcfirstbank.agent.orchestrator.routing.TierRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for detecting customer intent from natural language messages.
 * <p>
 * Supports multi-intent detection: a single message may contain multiple intents
 * (e.g. "check my balance and block my card" yields [BALANCE_INQUIRY, CARD_BLOCK]).
 * <p>
 * Implements a tiered detection strategy:
 * <ul>
 *   <li><b>Tier 0</b> - Keyword/regex-based detection for common, unambiguous queries (no LLM call)</li>
 *   <li><b>Tier 1</b> - Small/fast LLM for moderately complex queries</li>
 *   <li><b>Tier 2</b> - Full LLM with rich context for complex, multi-intent queries</li>
 * </ul>
 * <p>
 * A confidence threshold of 0.7 is applied: any intent below this threshold is replaced
 * with a CLARIFICATION_NEEDED intent prompting the customer for more detail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentDetectionService {

    private static final double CONFIDENCE_THRESHOLD = 0.7;

    private final ChatClient chatClient;
    private final TierRouter tierRouter;

    /**
     * Detect one or more customer intents from the given message using the tiered approach.
     *
     * @param message   the customer's message text
     * @param sessionId the conversation session identifier for context
     * @return list of detected intents with confidence and tier information
     */
    public List<DetectedIntent> detectIntents(String message, String sessionId) {
        // Tier 0: keyword-based detection - fast, no LLM call
        // Tier 0 can match multiple rules in a single message
        List<DetectedIntent> tier0Results = tierRouter.attemptTier0MultiDetection(message);
        if (!tier0Results.isEmpty()) {
            log.debug("Tier 0 keyword detection matched {} intent(s): {}",
                    tier0Results.size(),
                    tier0Results.stream().map(DetectedIntent::intent).toList());
            return applyConfidenceThreshold(tier0Results);
        }

        // Tier 1/2: LLM-based detection for complex queries
        List<DetectedIntent> llmResults = detectWithLlm(message, sessionId);
        return applyConfidenceThreshold(llmResults);
    }

    /**
     * Single-intent convenience method for backward compatibility.
     *
     * @param message   the customer's message text
     * @param sessionId the conversation session identifier for context
     * @return the primary detected intent
     */
    public DetectedIntent detectIntent(String message, String sessionId) {
        List<DetectedIntent> intents = detectIntents(message, sessionId);
        return intents.isEmpty()
                ? new DetectedIntent("UNKNOWN", 0.0, 2, Map.of())
                : intents.getFirst();
    }

    /**
     * Apply confidence threshold: any intent below {@value #CONFIDENCE_THRESHOLD}
     * is replaced with CLARIFICATION_NEEDED.
     */
    private List<DetectedIntent> applyConfidenceThreshold(List<DetectedIntent> intents) {
        if (intents.isEmpty()) {
            return List.of(new DetectedIntent("CLARIFICATION_NEEDED", 0.0, 2, Map.of()));
        }

        List<DetectedIntent> result = new ArrayList<>();
        for (DetectedIntent intent : intents) {
            if (intent.confidence() < CONFIDENCE_THRESHOLD) {
                log.info("Intent {} has low confidence {}, returning CLARIFICATION_NEEDED",
                        intent.intent(), intent.confidence());
                result.add(new DetectedIntent(
                        "CLARIFICATION_NEEDED",
                        intent.confidence(),
                        intent.tier(),
                        intent.parameters()
                ));
            } else {
                result.add(intent);
            }
        }
        return result;
    }

    /**
     * LLM-based intent detection for queries that cannot be resolved by keyword matching.
     * The prompt explicitly asks for multiple intents when present.
     */
    private List<DetectedIntent> detectWithLlm(String message, String sessionId) {
        log.debug("Using LLM for intent detection: sessionId={}", sessionId);

        String prompt = """
                You are an intent detection engine for IDFC First Bank's customer service system.
                Analyze the following customer message and detect ALL intents present.
                A single message may contain MULTIPLE intents (e.g. "check my balance and block my card").

                Respond in EXACTLY this format for EACH intent detected (no markdown, no extra text).
                If multiple intents are found, repeat the block for each one separated by a blank line:

                INTENT: <intent_name>
                CONFIDENCE: <0.0-1.0>
                PARAMETERS: <key1=value1,key2=value2 or NONE>

                Supported intents:
                - BALANCE_INQUIRY: Customer wants to check account balance
                - MINI_STATEMENT: Customer wants recent transaction history
                - FUND_TRANSFER: Customer wants to transfer money
                - CHEQUE_STATUS: Customer wants to check cheque status
                - CHEQUE_BOOK_REQUEST: Customer wants a new cheque book
                - FD_CREATION: Customer wants to create a fixed deposit
                - ACCOUNT_DETAILS: Customer wants account information
                - INTEREST_CERTIFICATE: Customer wants interest certificate
                - CARD_BLOCK: Customer wants to block a card
                - CARD_ACTIVATE: Customer wants to activate a card
                - CARD_LIMIT: Customer wants to change card limit
                - REWARD_POINTS: Customer wants to check or redeem reward points
                - DISPUTE_RAISE: Customer wants to raise a transaction dispute
                - LOAN_ELIGIBILITY: Customer wants to check loan eligibility
                - LOAN_EMI_QUERY: Customer wants EMI information
                - LOAN_PREPAYMENT: Customer wants to prepay a loan
                - LOAN_INQUIRY: Customer wants general information about loans
                - LOAN_APPLICATION: Customer wants to apply for a loan
                - GENERAL_INQUIRY: General banking question
                - COMPLAINT: Customer has a complaint
                - UNKNOWN: Cannot determine intent

                Customer message: %s
                """.formatted(message);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseLlmResponse(response, message);

        } catch (Exception e) {
            log.error("LLM intent detection failed, falling back to UNKNOWN: {}", e.getMessage(), e);
            return List.of(new DetectedIntent("UNKNOWN", 0.0, 2, Map.of()));
        }
    }

    /**
     * Parse the structured LLM response into a list of {@link DetectedIntent}.
     * Supports multiple intent blocks separated by blank lines.
     */
    private List<DetectedIntent> parseLlmResponse(String response, String originalMessage) {
        if (response == null || response.isBlank()) {
            return List.of(new DetectedIntent("UNKNOWN", 0.0, 2, Map.of()));
        }

        List<DetectedIntent> intents = new ArrayList<>();

        // Split response into blocks (separated by blank lines)
        String[] blocks = response.split("\\n\\s*\\n");

        for (String block : blocks) {
            String intent = null;
            double confidence = 0.5;
            Map<String, String> parameters = new HashMap<>();

            // Parse INTENT line
            Pattern intentPattern = Pattern.compile("INTENT:\\s*(.+)", Pattern.CASE_INSENSITIVE);
            Matcher intentMatcher = intentPattern.matcher(block);
            if (intentMatcher.find()) {
                intent = intentMatcher.group(1).trim().toUpperCase();
            }

            if (intent == null) {
                continue; // skip blocks without an INTENT line
            }

            // Parse CONFIDENCE line
            Pattern confPattern = Pattern.compile("CONFIDENCE:\\s*(\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE);
            Matcher confMatcher = confPattern.matcher(block);
            if (confMatcher.find()) {
                try {
                    confidence = Double.parseDouble(confMatcher.group(1));
                    confidence = Math.max(0.0, Math.min(1.0, confidence));
                } catch (NumberFormatException e) {
                    confidence = 0.5;
                }
            }

            // Parse PARAMETERS line
            Pattern paramPattern = Pattern.compile("PARAMETERS:\\s*(.+)", Pattern.CASE_INSENSITIVE);
            Matcher paramMatcher = paramPattern.matcher(block);
            if (paramMatcher.find()) {
                String paramStr = paramMatcher.group(1).trim();
                if (!"NONE".equalsIgnoreCase(paramStr)) {
                    for (String pair : paramStr.split(",")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) {
                            parameters.put(kv[0].trim(), kv[1].trim());
                        }
                    }
                }
            }

            // Determine tier: high confidence and clear intent = tier 1; otherwise tier 2
            int tier = confidence >= 0.8 ? 1 : 2;

            log.info("LLM intent detection result: intent={}, confidence={}, tier={}, params={}",
                    intent, confidence, tier, parameters);

            intents.add(new DetectedIntent(intent, confidence, tier, parameters));
        }

        if (intents.isEmpty()) {
            return List.of(new DetectedIntent("UNKNOWN", 0.0, 2, Map.of()));
        }

        return intents;
    }
}
