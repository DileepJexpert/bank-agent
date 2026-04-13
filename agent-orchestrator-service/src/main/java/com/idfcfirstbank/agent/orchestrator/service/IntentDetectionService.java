package com.idfcfirstbank.agent.orchestrator.service;

import com.idfcfirstbank.agent.common.llm.LlmRouter;
import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import com.idfcfirstbank.agent.orchestrator.routing.TierRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects customer intent from natural language using a tiered approach.
 * <p>
 * Tier 0: keyword/regex — no LLM call, ~40% of queries.<br>
 * Tier 1/2: LlmRouter — for complex or multi-intent messages.<br>
 * <p>
 * Uses {@link LlmRouter} (RestClient-based) instead of Spring AI ChatClient.
 * Switch LLM provider by changing llm.provider in application.yml only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentDetectionService {

    private static final double CONFIDENCE_THRESHOLD = 0.7;

    private static final String SYSTEM_PROMPT = """
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
            """;

    private final LlmRouter llmRouter;
    private final TierRouter tierRouter;

    /**
     * Detect one or more customer intents from the message using tiered approach.
     */
    public List<DetectedIntent> detectIntents(String message, String sessionId) {
        // Tier 0: keyword-based — fast, no LLM call
        List<DetectedIntent> tier0Results = tierRouter.attemptTier0MultiDetection(message);
        if (!tier0Results.isEmpty()) {
            log.debug("Tier 0 matched {} intent(s): {}",
                    tier0Results.size(),
                    tier0Results.stream().map(DetectedIntent::intent).toList());
            return applyConfidenceThreshold(tier0Results);
        }

        // Tier 1/2: LLM-based detection
        List<DetectedIntent> llmResults = detectWithLlm(message, sessionId);
        return applyConfidenceThreshold(llmResults);
    }

    /**
     * Single-intent convenience method for backward compatibility.
     */
    public DetectedIntent detectIntent(String message, String sessionId) {
        List<DetectedIntent> intents = detectIntents(message, sessionId);
        return intents.isEmpty()
                ? new DetectedIntent("UNKNOWN", 0.0, 2, Map.of())
                : intents.getFirst();
    }

    private List<DetectedIntent> applyConfidenceThreshold(List<DetectedIntent> intents) {
        if (intents.isEmpty()) {
            return List.of(new DetectedIntent("CLARIFICATION_NEEDED", 0.0, 2, Map.of()));
        }
        List<DetectedIntent> result = new ArrayList<>();
        for (DetectedIntent intent : intents) {
            if (intent.confidence() < CONFIDENCE_THRESHOLD) {
                log.info("Low confidence ({}) for intent {}, returning CLARIFICATION_NEEDED",
                        intent.confidence(), intent.intent());
                result.add(new DetectedIntent("CLARIFICATION_NEEDED", intent.confidence(),
                        intent.tier(), intent.parameters()));
            } else {
                result.add(intent);
            }
        }
        return result;
    }

    private List<DetectedIntent> detectWithLlm(String message, String sessionId) {
        log.debug("LLM intent detection: sessionId={}", sessionId);
        try {
            String response = llmRouter.chat(SYSTEM_PROMPT, message);
            return parseLlmResponse(response);
        } catch (Exception e) {
            log.error("LLM intent detection failed, falling back to UNKNOWN: {}", e.getMessage());
            return List.of(new DetectedIntent("UNKNOWN", 0.0, 2, Map.of()));
        }
    }

    private List<DetectedIntent> parseLlmResponse(String response) {
        if (response == null || response.isBlank()) {
            return List.of(new DetectedIntent("UNKNOWN", 0.0, 2, Map.of()));
        }

        List<DetectedIntent> intents = new ArrayList<>();
        String[] blocks = response.split("\\n\\s*\\n");

        for (String block : blocks) {
            String intent = null;
            double confidence = 0.5;
            Map<String, String> parameters = new HashMap<>();

            Matcher intentMatcher = Pattern.compile("INTENT:\\s*(.+)", Pattern.CASE_INSENSITIVE).matcher(block);
            if (intentMatcher.find()) {
                intent = intentMatcher.group(1).trim().toUpperCase();
            }
            if (intent == null) continue;

            Matcher confMatcher = Pattern.compile("CONFIDENCE:\\s*(\\d+\\.?\\d*)", Pattern.CASE_INSENSITIVE).matcher(block);
            if (confMatcher.find()) {
                try {
                    confidence = Math.max(0.0, Math.min(1.0, Double.parseDouble(confMatcher.group(1))));
                } catch (NumberFormatException ignored) {
                    confidence = 0.5;
                }
            }

            Matcher paramMatcher = Pattern.compile("PARAMETERS:\\s*(.+)", Pattern.CASE_INSENSITIVE).matcher(block);
            if (paramMatcher.find()) {
                String paramStr = paramMatcher.group(1).trim();
                if (!"NONE".equalsIgnoreCase(paramStr)) {
                    for (String pair : paramStr.split(",")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) parameters.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }

            int tier = confidence >= 0.8 ? 1 : 2;
            log.info("LLM intent: {} confidence={} tier={} params={}", intent, confidence, tier, parameters);
            intents.add(new DetectedIntent(intent, confidence, tier, parameters));
        }

        return intents.isEmpty()
                ? List.of(new DetectedIntent("UNKNOWN", 0.0, 2, Map.of()))
                : intents;
    }
}
