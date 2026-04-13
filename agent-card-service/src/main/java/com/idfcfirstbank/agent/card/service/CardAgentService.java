package com.idfcfirstbank.agent.card.service;

import com.idfcfirstbank.agent.card.model.CardAgentRequest;
import com.idfcfirstbank.agent.card.model.CardAgentResponse;
import com.idfcfirstbank.agent.card.model.DisputeRequest;
import com.idfcfirstbank.agent.card.model.RewardPointsResponse;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import com.idfcfirstbank.agent.common.util.MaskingUtils;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main service for the Card Agent.
 * Uses {@link LlmRouter} (plain RestClient) instead of Spring AI ChatClient.
 * Switch LLM provider by changing llm.provider in application.yml only.
 * <p>
 * CRITICAL: All card numbers are masked — only last 4 digits ever visible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardAgentService {

    private static final String SYSTEM_PROMPT = """
            You are the Card Agent for IDFC First Bank. You handle card-related queries:
            card blocking, activation, reward points, dispute raising, EMI conversion, limit management.

            CRITICAL SECURITY RULES:
            - NEVER display or include full card numbers in any response.
            - Always refer to cards by their last 4 digits only (e.g., "card ending in 1234").
            - Verify the customer's identity context before performing sensitive operations.
            - For card blocking requests, act immediately without unnecessary conversation.
            - Format currency amounts in INR with proper formatting.
            """;

    /** Allow-listed context keys forwarded to the LLM — no raw IDs or PII beyond these. */
    private static final List<String> ALLOWED_CONTEXT_KEYS = List.of(
            "cardLast4", "transactionAmount", "merchantName", "intent",
            "tenure", "limitAmount", "cardType"
    );

    private final LlmRouter llmRouter;
    private final VaultClient vaultClient;
    private final RestTemplate restTemplate;

    @Value("${agent.mcp.card-management-url:http://mcp-card-management-server:8089}")
    private String cardManagementUrl;

    @Value("${agent.mcp.notification-url:http://notification-service:8090}")
    private String notificationUrl;

    public CardAgentResponse processCardQuery(CardAgentRequest request) {
        String intent = request.intent() != null ? request.intent().toUpperCase() : "GENERAL";
        List<String> mcpCallsMade = new ArrayList<>();

        PolicyDecision decision = evaluateVaultPolicy(request.customerId(), intent, request.sessionId());
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault denied card op: sessionId={}, intent={}", request.sessionId(), intent);
            return CardAgentResponse.denied(request.sessionId(), decision.reason(), intent);
        }
        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            return CardAgentResponse.escalated(request.sessionId(), intent);
        }

        return switch (intent) {
            case "CARD_BLOCK" -> processCardBlock(request, mcpCallsMade);
            case "REWARD_POINTS" -> processRewardPoints(request, mcpCallsMade);
            case "DISPUTE_RAISE" -> processDisputeRaise(request, mcpCallsMade);
            case "CARD_ACTIVATE" -> processCardActivate(request, mcpCallsMade);
            case "EMI_CONVERT" -> processEmiConvert(request, mcpCallsMade);
            default -> processGeneral(request, mcpCallsMade);
        };
    }

    /** Direct card block — Tier 1, urgent, no LLM. */
    public CardAgentResponse blockCardDirect(String customerId, String cardLast4, String reason) {
        List<String> mcpCallsMade = new ArrayList<>();
        PolicyDecision decision = evaluateVaultPolicy(customerId, "CARD_BLOCK", null);
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            return CardAgentResponse.denied(null, decision.reason(), "CARD_BLOCK");
        }
        try {
            Map<String, Object> result = callMcpTool("/tools/blockCard",
                    Map.of("customerId", customerId, "cardLast4", cardLast4, "reason", reason));
            mcpCallsMade.add("blockCard");
            sendBlockNotification(customerId, cardLast4);
            mcpCallsMade.add("sendNotification");

            return new CardAgentResponse(null,
                    String.format("Your card ending in %s has been blocked. Block reference: %s. "
                                    + "If you did not request this, call our 24x7 helpline immediately.",
                            cardLast4, result.getOrDefault("blockReference", "N/A")),
                    Map.of("cardLast4", cardLast4, "blockReference",
                            String.valueOf(result.getOrDefault("blockReference", "N/A"))),
                    mcpCallsMade, "TIER_1");
        } catch (Exception e) {
            log.error("Card block failed: cardLast4=****{}", cardLast4, e);
            return new CardAgentResponse(null,
                    "We encountered an issue blocking your card. Please call our 24x7 helpline immediately.",
                    Map.of("cardLast4", cardLast4), mcpCallsMade, "TIER_1");
        }
    }

    /** Direct reward points — Tier 0, no LLM. */
    public RewardPointsResponse getRewardPointsDirect(String customerId) {
        try {
            String toolPath = UriComponentsBuilder.fromPath("/tools/getRewardPoints")
                    .queryParam("customerId", customerId)
                    .build()
                    .toUriString();
            Map<String, Object> result = callMcpTool(toolPath, null);
            return new RewardPointsResponse(
                    ((Number) result.getOrDefault("totalPoints", 0)).longValue(),
                    new BigDecimal(String.valueOf(result.getOrDefault("valueInRupees", "0"))),
                    String.valueOf(result.getOrDefault("expiryDate", "N/A"))
            );
        } catch (Exception e) {
            log.error("Reward points fetch failed for session", e);
            return new RewardPointsResponse(0L, BigDecimal.ZERO, "N/A");
        }
    }

    /** Direct dispute raise — Tier 2. */
    public CardAgentResponse raiseDisputeDirect(DisputeRequest request) {
        List<String> mcpCallsMade = new ArrayList<>();
        PolicyDecision decision = evaluateVaultPolicy(request.customerId(), "DISPUTE_RAISE", null);
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            return CardAgentResponse.denied(null, decision.reason(), "DISPUTE_RAISE");
        }
        try {
            Map<String, Object> result = callMcpTool("/tools/raiseDispute", Map.of(
                    "customerId", request.customerId(),
                    "transactionId", request.transactionId(),
                    "reason", request.reason(),
                    "amount", request.amount().toString()
            ));
            mcpCallsMade.add("raiseDispute");
            return new CardAgentResponse(null,
                    String.format("Dispute registered. ID: %s, expected resolution: %s.",
                            result.getOrDefault("disputeId", "N/A"),
                            result.getOrDefault("expectedResolutionDate", "N/A")),
                    Map.of("disputeId", String.valueOf(result.getOrDefault("disputeId", "N/A")),
                            "status", String.valueOf(result.getOrDefault("status", "N/A"))),
                    mcpCallsMade, "TIER_2");
        } catch (Exception e) {
            log.error("Dispute raise failed", e);
            return new CardAgentResponse(null,
                    "Unable to raise dispute. Please try again or contact support.",
                    Map.of(), mcpCallsMade, "TIER_2");
        }
    }

    // ── Tier 1: Card Block ──

    private CardAgentResponse processCardBlock(CardAgentRequest request, List<String> mcpCallsMade) {
        String cardLast4 = extractCardLast4(request);
        if (cardLast4.isBlank()) {
            log.warn("Card block requested but cardLast4 not provided: sessionId={}", request.sessionId());
            return new CardAgentResponse(request.sessionId(),
                    "Please specify which card you'd like to block (provide the last 4 digits).",
                    Map.of(), mcpCallsMade, "TIER_1");
        }
        return blockCardDirect(request.customerId(), cardLast4,
                request.message() != null ? request.message() : "CUSTOMER_REQUEST");
    }

    // ── Tier 0: Reward Points (no LLM) ──

    private CardAgentResponse processRewardPoints(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            String toolPath = UriComponentsBuilder.fromPath("/tools/getRewardPoints")
                    .queryParam("customerId", request.customerId())
                    .build()
                    .toUriString();
            Map<String, Object> result = callMcpTool(toolPath, null);
            mcpCallsMade.add("getRewardPoints");
            return new CardAgentResponse(request.sessionId(),
                    String.format("You have %s reward points valued at INR %s.%s",
                            result.getOrDefault("totalPoints", "0"),
                            result.getOrDefault("valueInRupees", "0"),
                            result.containsKey("expiryDate")
                                    ? " Points expire on " + result.get("expiryDate") + "." : ""),
                    Map.of("totalPoints", String.valueOf(result.getOrDefault("totalPoints", "0")),
                            "valueInRupees", String.valueOf(result.getOrDefault("valueInRupees", "0"))),
                    mcpCallsMade, "TIER_0");
        } catch (Exception e) {
            log.error("Reward points failed: sessionId={}", request.sessionId(), e);
            return new CardAgentResponse(request.sessionId(),
                    "Unable to retrieve reward points. Please try again later.",
                    Map.of(), mcpCallsMade, "TIER_0");
        }
    }

    // ── Tier 2: Dispute Raise (LLM-assisted) ──

    private CardAgentResponse processDisputeRaise(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            String llmResponse = llmRouter.chat(SYSTEM_PROMPT, buildAllowlistedPrompt(request));

            if (request.customerContext() != null && request.customerContext().containsKey("transactionId")) {
                Map<String, Object> result = callMcpTool("/tools/raiseDispute", Map.of(
                        "customerId", request.customerId(),
                        "transactionId", request.customerContext().get("transactionId"),
                        "reason", request.message() != null ? request.message() : "Customer dispute",
                        "amount", request.customerContext().getOrDefault("amount", "0")
                ));
                mcpCallsMade.add("raiseDispute");
                return new CardAgentResponse(request.sessionId(),
                        MaskingUtils.maskCardNumber(llmResponse) + " Dispute ID: "
                                + result.getOrDefault("disputeId", "N/A"),
                        Map.of("disputeId", String.valueOf(result.getOrDefault("disputeId", "N/A"))),
                        mcpCallsMade, "TIER_2");
            }
            return new CardAgentResponse(request.sessionId(),
                    MaskingUtils.maskCardNumber(llmResponse), Map.of(), mcpCallsMade, "TIER_2");
        } catch (Exception e) {
            log.error("Dispute processing error: sessionId={}", request.sessionId(), e);
            return new CardAgentResponse(request.sessionId(),
                    "Unable to process dispute. Please try again.", Map.of(), mcpCallsMade, "TIER_2");
        }
    }

    // ── Tier 1: Card Activate ──

    private CardAgentResponse processCardActivate(CardAgentRequest request, List<String> mcpCallsMade) {
        String cardLast4 = extractCardLast4(request);
        if (cardLast4.isBlank()) {
            log.warn("Card activate requested but cardLast4 not provided: sessionId={}", request.sessionId());
            return new CardAgentResponse(request.sessionId(),
                    "Please specify which card you'd like to activate (provide the last 4 digits).",
                    Map.of(), mcpCallsMade, "TIER_1");
        }
        try {
            Map<String, Object> result = callMcpTool("/tools/activateCard",
                    Map.of("customerId", request.customerId(), "cardLast4", cardLast4));
            mcpCallsMade.add("activateCard");
            return new CardAgentResponse(request.sessionId(),
                    String.format("Card ending in %s activated at %s. You can now use it for transactions.",
                            cardLast4, result.getOrDefault("activatedAt", "now")),
                    Map.of("cardLast4", cardLast4, "status",
                            String.valueOf(result.getOrDefault("status", "ACTIVATED"))),
                    mcpCallsMade, "TIER_1");
        } catch (Exception e) {
            log.error("Card activate failed: sessionId={}", request.sessionId(), e);
            return new CardAgentResponse(request.sessionId(),
                    "Unable to activate card. Please try again or visit a branch.",
                    Map.of("cardLast4", cardLast4), mcpCallsMade, "TIER_1");
        }
    }

    // ── Tier 2: EMI Conversion (LLM-assisted) ──

    private CardAgentResponse processEmiConvert(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            String llmResponse = llmRouter.chat(SYSTEM_PROMPT, buildAllowlistedPrompt(request));

            if (request.customerContext() != null && request.customerContext().containsKey("transactionId")) {
                Map<String, Object> result = callMcpTool("/tools/convertToEMI", Map.of(
                        "customerId", request.customerId(),
                        "transactionId", request.customerContext().get("transactionId"),
                        "tenure", request.customerContext().getOrDefault("tenure", "12")
                ));
                mcpCallsMade.add("convertToEMI");
                return new CardAgentResponse(request.sessionId(),
                        MaskingUtils.maskCardNumber(llmResponse)
                                + String.format(" EMI: INR %s/month at %s%%, total: INR %s. ID: %s.",
                                result.getOrDefault("emiAmount", "N/A"),
                                result.getOrDefault("interestRate", "N/A"),
                                result.getOrDefault("totalCost", "N/A"),
                                result.getOrDefault("emiId", "N/A")),
                        Map.of("emiId", String.valueOf(result.getOrDefault("emiId", "N/A")),
                                "emiAmount", String.valueOf(result.getOrDefault("emiAmount", "N/A"))),
                        mcpCallsMade, "TIER_2");
            }
            return new CardAgentResponse(request.sessionId(),
                    MaskingUtils.maskCardNumber(llmResponse), Map.of(), mcpCallsMade, "TIER_2");
        } catch (Exception e) {
            log.error("EMI conversion error: sessionId={}", request.sessionId(), e);
            return new CardAgentResponse(request.sessionId(),
                    "Unable to process EMI conversion. Please try again.",
                    Map.of(), mcpCallsMade, "TIER_2");
        }
    }

    // ── General (LLM) ──

    private CardAgentResponse processGeneral(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            String response = llmRouter.chat(SYSTEM_PROMPT, buildAllowlistedPrompt(request));
            return new CardAgentResponse(request.sessionId(),
                    MaskingUtils.maskCardNumber(response), Map.of(), mcpCallsMade, "GENERAL");
        } catch (Exception e) {
            log.error("General card query error: sessionId={}", request.sessionId(), e);
            return new CardAgentResponse(request.sessionId(),
                    "Unable to process request. Please try again.",
                    Map.of(), mcpCallsMade, "GENERAL");
        }
    }

    // ── Helpers ──

    private PolicyDecision evaluateVaultPolicy(String customerId, String intent, String sessionId) {
        return vaultClient.evaluatePolicy("card-agent",
                intent != null ? intent.toLowerCase() : "card_query",
                "customer:" + customerId,
                Map.of("sessionId", sessionId != null ? sessionId : "", "channel", "orchestrator"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callMcpTool(String toolPath, Map<String, Object> requestBody) {
        String url = cardManagementUrl + toolPath;
        try {
            if (requestBody != null) {
                ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                        url, HttpMethod.POST, new HttpEntity<>(requestBody),
                        new ParameterizedTypeReference<>() {});
                return resp.getBody() != null ? resp.getBody() : Map.of();
            } else {
                ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                        url, HttpMethod.GET, null,
                        new ParameterizedTypeReference<>() {});
                return resp.getBody() != null ? resp.getBody() : Map.of();
            }
        } catch (RestClientException e) {
            // Strip query string before logging to avoid leaking PII (e.g. customerId params)
            String safeToolPath = toolPath.contains("?") ? toolPath.substring(0, toolPath.indexOf('?')) : toolPath;
            log.error("MCP call failed: {}, {}", safeToolPath, e.getMessage(), e);
            throw e;
        }
    }

    private void sendBlockNotification(String customerId, String cardLast4) {
        try {
            restTemplate.postForObject(notificationUrl + "/api/v1/notify",
                    Map.of("customerId", customerId, "type", "SMS",
                            "template", "CARD_BLOCKED", "parameters", Map.of("cardLast4", cardLast4)),
                    Map.class);
        } catch (Exception e) {
            log.warn("Block notification failed: {}", e.getMessage());
        }
    }

    private String extractCardLast4(CardAgentRequest request) {
        if (request.customerContext() != null && request.customerContext().containsKey("cardLast4")) {
            return request.customerContext().get("cardLast4");
        }
        return "";
    }

    /**
     * Build a prompt that contains only allow-listed fields from customerContext,
     * preventing raw transaction IDs and other identifiers from being forwarded to the LLM.
     */
    private String buildAllowlistedPrompt(CardAgentRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Customer message: ").append(request.message()).append("\n");
        if (request.intent() != null) prompt.append("Intent: ").append(request.intent()).append("\n");
        if (request.language() != null) prompt.append("Language: ").append(request.language()).append("\n");

        // Only include allow-listed context keys — no raw IDs or PII beyond card last-4
        if (request.customerContext() != null && !request.customerContext().isEmpty()) {
            Map<String, String> safeContext = new LinkedHashMap<>();
            for (String key : ALLOWED_CONTEXT_KEYS) {
                if (request.customerContext().containsKey(key)) {
                    String val = request.customerContext().get(key);
                    safeContext.put(key, "cardLast4".equals(key)
                            ? MaskingUtils.maskCardNumber(val) : val);
                }
            }
            if (!safeContext.isEmpty()) {
                prompt.append("Context: ").append(safeContext).append("\n");
            }
        }

        // Include up to last 3 turns of conversation history; mask any card numbers in history entries
        if (request.conversationHistory() != null && !request.conversationHistory().isEmpty()) {
            List<String> history = request.conversationHistory();
            int start = Math.max(0, history.size() - 3);
            prompt.append("Recent conversation:\n");
            history.subList(start, history.size())
                   .forEach(e -> prompt.append("  - ").append(MaskingUtils.maskCardNumber(e)).append("\n"));
        }

        prompt.append("\nProcess this card request. NEVER include full card numbers in responses.");
        return prompt.toString();
    }
}
