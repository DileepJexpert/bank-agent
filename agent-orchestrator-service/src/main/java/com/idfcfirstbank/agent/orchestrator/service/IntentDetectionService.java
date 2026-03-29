package com.idfcfirstbank.agent.orchestrator.service;

import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import com.idfcfirstbank.agent.orchestrator.routing.TierRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for detecting customer intent from natural language messages.
 * <p>
 * Implements a tiered detection strategy:
 * <ul>
 *   <li><b>Tier 0</b> - Keyword/regex-based detection for common, unambiguous queries (no LLM call)</li>
 *   <li><b>Tier 1</b> - Small/fast LLM for moderately complex queries</li>
 *   <li><b>Tier 2</b> - Full LLM with rich context for complex, multi-intent queries</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentDetectionService {

    private final ChatClient chatClient;
    private final TierRouter tierRouter;

    /**
     * Detect customer intent from the given message using the tiered approach.
     *
     * @param message   the customer's message text
     * @param sessionId the conversation session identifier for context
     * @return the detected intent with confidence and tier information
     */
    public DetectedIntent detectIntent(String message, String sessionId) {
        // Tier 0: keyword-based detection - fast, no LLM call
        Optional<DetectedIntent> tier0Result = tierRouter.attemptTier0Detection(message);
        if (tier0Result.isPresent()) {
            log.debug("Tier 0 keyword detection matched: intent={}", tier0Result.get().intent());
            return tier0Result.get();
        }

        // Tier 1/2: LLM-based detection for complex queries
        return detectWithLlm(message, sessionId);
    }

    /**
     * LLM-based intent detection for queries that cannot be resolved by keyword matching.
     */
    private DetectedIntent detectWithLlm(String message, String sessionId) {
        log.debug("Using LLM for intent detection: sessionId={}", sessionId);

        String prompt = """
                You are an intent detection engine for IDFC First Bank's customer service system.
                Analyze the following customer message and detect the intent.

                Respond in EXACTLY this format (no markdown, no extra text):
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
                - CARD_LIMIT: Customer wants to change card limit
                - LOAN_INQUIRY: Customer wants information about loans
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
            return new DetectedIntent("UNKNOWN", 0.0, 2, Map.of());
        }
    }

    /**
     * Parse the structured LLM response into a {@link DetectedIntent}.
     */
    private DetectedIntent parseLlmResponse(String response, String originalMessage) {
        String intent = "UNKNOWN";
        double confidence = 0.5;
        Map<String, String> parameters = new HashMap<>();

        if (response == null || response.isBlank()) {
            return new DetectedIntent(intent, 0.0, 2, parameters);
        }

        // Parse INTENT line
        Pattern intentPattern = Pattern.compile("INTENT:\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher intentMatcher = intentPattern.matcher(response);
        if (intentMatcher.find()) {
            intent = intentMatcher.group(1).trim().toUpperCase();
        }

        // Parse CONFIDENCE line
        Pattern confPattern = Pattern.compile("CONFIDENCE:\\s*(\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE);
        Matcher confMatcher = confPattern.matcher(response);
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
        Matcher paramMatcher = paramPattern.matcher(response);
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

        // Determine tier: if confidence is high and intent is clear, it is tier 1; otherwise tier 2
        int tier = confidence >= 0.8 ? 1 : 2;

        log.info("LLM intent detection result: intent={}, confidence={}, tier={}, params={}",
                intent, confidence, tier, parameters);

        return new DetectedIntent(intent, confidence, tier, parameters);
    }
}
