package com.idfcfirstbank.agent.orchestrator.model;

import java.util.List;

/**
 * Outbound chat response returned to the customer.
 *
 * @param sessionId           the conversation session identifier
 * @param message             the response message text
 * @param agentType           which domain agent handled the query (e.g. ACCOUNT, LOANS)
 * @param intent              the primary detected customer intent
 * @param confidence          confidence score of the primary intent detection (0.0 - 1.0)
 * @param escalated           whether the query was escalated to a human agent
 * @param intents             all detected intents (for multi-intent queries)
 * @param clarificationNeeded whether the system needs clarification from the customer
 * @param tier                the detection tier that resolved the primary intent
 * @param aiModel             the AI model used for detection (e.g. "llama3.1" or "keyword-fallback")
 * @param intentConfidence    confidence from the AI intent detector (0.0 - 1.0)
 * @param detectedLanguage    language detected from the customer message (e.g. "hi", "en", "hi-en")
 * @param toolsCalled         list of AI tools/agents invoked during processing
 */
public record ChatResponse(
        String sessionId,
        String message,
        String agentType,
        String intent,
        double confidence,
        boolean escalated,
        List<DetectedIntent> intents,
        boolean clarificationNeeded,
        int tier,
        String aiModel,
        double intentConfidence,
        String detectedLanguage,
        List<String> toolsCalled
) {
    /**
     * Backward-compatible constructor for single-intent responses (no AI fields).
     */
    public ChatResponse(String sessionId, String message, String agentType,
                        String intent, double confidence, boolean escalated) {
        this(sessionId, message, agentType, intent, confidence, escalated,
                List.of(), false, 0, "keyword-fallback", confidence, "en", List.of());
    }

    /**
     * Backward-compatible constructor without AI fields (uses defaults).
     */
    public ChatResponse(String sessionId, String message, String agentType,
                        String intent, double confidence, boolean escalated,
                        List<DetectedIntent> intents, boolean clarificationNeeded, int tier) {
        this(sessionId, message, agentType, intent, confidence, escalated,
                intents, clarificationNeeded, tier, "keyword-fallback", confidence, "en", List.of());
    }
}
