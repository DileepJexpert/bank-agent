package com.idfcfirstbank.agent.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LLM-based intent detector using Ollama (Llama 3.1) via Spring AI.
 * <p>
 * Activated when {@code ai.enabled=true}. Extracts intent, entities,
 * language, and tier from customer messages in Hindi, English, or Hinglish.
 * <p>
 * Falls back gracefully to keyword-based detection if the LLM call fails.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class AiIntentDetector {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /** Maps intents to their tier level. */
    private static final Map<String, Integer> INTENT_TIER_MAP = Map.ofEntries(
            Map.entry("BALANCE_CHECK", 0),
            Map.entry("BALANCE_INQUIRY", 0),
            Map.entry("MINI_STATEMENT", 0),
            Map.entry("CARD_BLOCK", 1),
            Map.entry("CREATE_FD", 1),
            Map.entry("FD_CREATION", 1),
            Map.entry("REWARD_POINTS", 1),
            Map.entry("CARD_ACTIVATE", 1),
            Map.entry("CARD_LIMIT", 1),
            Map.entry("LOAN_ELIGIBILITY", 2),
            Map.entry("TRANSFER_MONEY", 2),
            Map.entry("FUND_TRANSFER", 2),
            Map.entry("COMPLAINT", 2),
            Map.entry("HUMAN_ESCALATION", 2),
            Map.entry("LOAN_EMI_QUERY", 2),
            Map.entry("LOAN_PREPAYMENT", 2),
            Map.entry("LOAN_APPLICATION", 2),
            Map.entry("ACCOUNT_DETAILS", 0),
            Map.entry("CHEQUE_STATUS", 0),
            Map.entry("CHEQUE_BOOK_REQUEST", 1),
            Map.entry("DISPUTE_RAISE", 2),
            Map.entry("GENERAL_INQUIRY", 1)
    );

    public AiIntentDetector(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.chatClient = builder.defaultSystem("""
                You are an intent detector for IDFC First Bank.
                Customer messages may be in Hindi, English, or Hinglish.

                Extract intent and entities from the message.
                Respond ONLY with valid JSON, no markdown, no explanation:
                {
                  "intents": ["BALANCE_CHECK"],
                  "entities": {"amount": null, "tenure": null},
                  "language": "hi",
                  "confidence": 0.95
                }

                Valid intents: BALANCE_CHECK, BALANCE_INQUIRY, MINI_STATEMENT, CARD_BLOCK,
                CARD_ACTIVATE, CARD_LIMIT, LOAN_ELIGIBILITY, LOAN_EMI_QUERY, LOAN_PREPAYMENT,
                LOAN_APPLICATION, CREATE_FD, FD_CREATION, TRANSFER_MONEY, FUND_TRANSFER,
                REWARD_POINTS, COMPLAINT, HUMAN_ESCALATION, GENERAL_INQUIRY,
                ACCOUNT_DETAILS, CHEQUE_STATUS, CHEQUE_BOOK_REQUEST, DISPUTE_RAISE

                Rules:
                - Return the most specific intent possible
                - If multiple intents are present, list all of them
                - confidence must be between 0.0 and 1.0
                - language should be: en, hi, hi-en, ta, te, bn, mr, gu
                """).build();
    }

    /**
     * Detect intents from a customer message using the LLM.
     *
     * @param message the customer's message
     * @return intent result with intents, entities, language, and confidence
     */
    public AiIntentResult detect(String message) {
        log.debug("AI intent detection for message: {}", message);

        try {
            String response = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();

            log.debug("AI intent detector raw response: {}", response);
            return parseResponse(response);

        } catch (Exception e) {
            log.warn("AI intent detection failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private AiIntentResult parseResponse(String response) {
        try {
            // Strip markdown code fences if present
            String json = response.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});

            List<String> intents = parsed.containsKey("intents")
                    ? (List<String>) parsed.get("intents")
                    : List.of("GENERAL_INQUIRY");

            Map<String, Object> entities = parsed.containsKey("entities")
                    ? (Map<String, Object>) parsed.get("entities")
                    : Map.of();

            String language = parsed.containsKey("language")
                    ? parsed.get("language").toString()
                    : "en";

            double confidence = 0.85;
            if (parsed.containsKey("confidence")) {
                Object confVal = parsed.get("confidence");
                if (confVal instanceof Number num) {
                    confidence = Math.max(0.0, Math.min(1.0, num.doubleValue()));
                }
            }

            // Convert to DetectedIntent list
            List<DetectedIntent> detectedIntents = new ArrayList<>();
            for (String intent : intents) {
                String normalizedIntent = intent.trim().toUpperCase();
                int tier = INTENT_TIER_MAP.getOrDefault(normalizedIntent, 2);

                Map<String, String> params = new HashMap<>();
                entities.forEach((k, v) -> {
                    if (v != null) {
                        params.put(k, v.toString());
                    }
                });

                detectedIntents.add(new DetectedIntent(normalizedIntent, confidence, tier, params));
            }

            if (detectedIntents.isEmpty()) {
                detectedIntents.add(new DetectedIntent("GENERAL_INQUIRY", 0.5, 1, Map.of()));
            }

            return new AiIntentResult(detectedIntents, language, confidence);

        } catch (Exception e) {
            log.warn("Failed to parse AI intent response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Result holder for AI-based intent detection.
     */
    public record AiIntentResult(
            List<DetectedIntent> intents,
            String language,
            double confidence
    ) {}
}
