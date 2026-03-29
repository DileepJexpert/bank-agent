package com.idfcfirstbank.agent.card.service;

import com.idfcfirstbank.agent.card.model.CardAgentRequest;
import com.idfcfirstbank.agent.card.model.CardAgentResponse;
import com.idfcfirstbank.agent.card.model.DisputeRequest;
import com.idfcfirstbank.agent.card.model.RewardPointsResponse;
import com.idfcfirstbank.agent.common.util.MaskingUtils;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main service for the Card Agent. Orchestrates tiered processing of card operations
 * with Spring AI function calling and policy enforcement via Vault.
 * <p>
 * Processing tiers:
 * <ul>
 *   <li><b>Tier 0 (REWARD_POINTS)</b>: No LLM - direct MCP call, format and return</li>
 *   <li><b>Tier 1 (CARD_BLOCK, CARD_ACTIVATE)</b>: Urgent - immediate MCP call, optional SMS notification</li>
 *   <li><b>Tier 2 (DISPUTE_RAISE, EMI_CONVERT)</b>: Uses ChatClient for conversation and detail collection</li>
 * </ul>
 * <p>
 * CRITICAL: All card numbers are masked in responses and logs. Only last 4 digits are ever visible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardAgentService {

    private final ChatClient chatClient;
    private final VaultClient vaultClient;
    private final RestTemplate restTemplate;

    @Value("${agent.mcp.card-management-url:http://mcp-card-management-server:8089}")
    private String cardManagementUrl;

    @Value("${agent.mcp.notification-url:http://notification-service:8090}")
    private String notificationUrl;

    /**
     * Process a card query with tiered execution based on intent.
     */
    public CardAgentResponse processCardQuery(CardAgentRequest request) {
        String intent = request.intent() != null ? request.intent().toUpperCase() : "GENERAL";
        List<String> mcpCallsMade = new ArrayList<>();

        // Vault policy check
        PolicyDecision decision = evaluateVaultPolicy(request.customerId(), intent, request.sessionId());
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault policy denied card operation: customerId={}, intent={}, reason={}",
                    request.customerId(), intent, decision.reason());
            return CardAgentResponse.denied(request.sessionId(), decision.reason(), intent);
        }
        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Vault policy requires escalation: customerId={}, intent={}", request.customerId(), intent);
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

    /**
     * Direct card block (Tier 1 - urgent, bypasses LLM).
     */
    public CardAgentResponse blockCardDirect(String customerId, String cardLast4, String reason) {
        List<String> mcpCallsMade = new ArrayList<>();

        PolicyDecision decision = evaluateVaultPolicy(customerId, "CARD_BLOCK", null);
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            return CardAgentResponse.denied(null, decision.reason(), "CARD_BLOCK");
        }

        try {
            Map<String, Object> mcpRequest = Map.of(
                    "customerId", customerId,
                    "cardLast4", cardLast4,
                    "reason", reason
            );

            Map<String, Object> result = callMcpTool("/tools/blockCard", mcpRequest);
            mcpCallsMade.add("blockCard");

            // Send SMS notification
            sendBlockNotification(customerId, cardLast4);
            mcpCallsMade.add("sendNotification");

            String responseMessage = String.format(
                    "Your card ending in %s has been blocked immediately. Block reference: %s. "
                            + "If you did not request this, please contact our 24x7 helpline immediately.",
                    cardLast4, result.getOrDefault("blockReference", "N/A"));

            return new CardAgentResponse(
                    null,
                    responseMessage,
                    Map.of("cardLast4", cardLast4, "blockReference",
                            String.valueOf(result.getOrDefault("blockReference", "N/A"))),
                    mcpCallsMade,
                    "TIER_1"
            );

        } catch (Exception e) {
            log.error("Failed to block card for customerId={}, cardLast4=****{}: {}",
                    customerId, cardLast4, e.getMessage(), e);
            return new CardAgentResponse(
                    null,
                    "We encountered an issue blocking your card. Please call our 24x7 helpline "
                            + "at 1800-xxx-xxxx for immediate assistance.",
                    Map.of("cardLast4", cardLast4),
                    mcpCallsMade,
                    "TIER_1"
            );
        }
    }

    /**
     * Direct reward points inquiry (Tier 0 - no LLM).
     */
    public RewardPointsResponse getRewardPointsDirect(String customerId) {
        try {
            Map<String, Object> result = callMcpTool("/tools/getRewardPoints?customerId=" + customerId, null);

            return new RewardPointsResponse(
                    ((Number) result.getOrDefault("totalPoints", 0)).longValue(),
                    new BigDecimal(String.valueOf(result.getOrDefault("valueInRupees", "0"))),
                    String.valueOf(result.getOrDefault("expiryDate", "N/A"))
            );

        } catch (Exception e) {
            log.error("Failed to fetch reward points for customerId={}: {}", customerId, e.getMessage(), e);
            return new RewardPointsResponse(0L, BigDecimal.ZERO, "N/A");
        }
    }

    /**
     * Direct dispute raise (Tier 2).
     */
    public CardAgentResponse raiseDisputeDirect(DisputeRequest request) {
        List<String> mcpCallsMade = new ArrayList<>();

        PolicyDecision decision = evaluateVaultPolicy(request.customerId(), "DISPUTE_RAISE", null);
        if (decision.decision() == PolicyDecision.Decision.DENY) {
            return CardAgentResponse.denied(null, decision.reason(), "DISPUTE_RAISE");
        }

        try {
            Map<String, Object> mcpRequest = Map.of(
                    "customerId", request.customerId(),
                    "transactionId", request.transactionId(),
                    "reason", request.reason(),
                    "amount", request.amount().toString()
            );

            Map<String, Object> result = callMcpTool("/tools/raiseDispute", mcpRequest);
            mcpCallsMade.add("raiseDispute");

            String responseMessage = String.format(
                    "Your dispute has been registered successfully. Dispute ID: %s. "
                            + "Expected resolution date: %s. We will keep you updated via SMS and email.",
                    result.getOrDefault("disputeId", "N/A"),
                    result.getOrDefault("expectedResolutionDate", "N/A"));

            return new CardAgentResponse(
                    null,
                    responseMessage,
                    Map.of("disputeId", String.valueOf(result.getOrDefault("disputeId", "N/A")),
                            "status", String.valueOf(result.getOrDefault("status", "N/A"))),
                    mcpCallsMade,
                    "TIER_2"
            );

        } catch (Exception e) {
            log.error("Failed to raise dispute for customerId={}: {}", request.customerId(), e.getMessage(), e);
            return new CardAgentResponse(
                    null,
                    "We encountered an issue raising your dispute. Please try again or contact support.",
                    Map.of(),
                    mcpCallsMade,
                    "TIER_2"
            );
        }
    }

    // ── Tier 1: Card Block (urgent) ──

    private CardAgentResponse processCardBlock(CardAgentRequest request, List<String> mcpCallsMade) {
        String cardLast4 = extractCardLast4(request);
        return blockCardDirect(request.customerId(), cardLast4,
                request.message() != null ? request.message() : "CUSTOMER_REQUEST");
    }

    // ── Tier 0: Reward Points (no LLM) ──

    private CardAgentResponse processRewardPoints(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            Map<String, Object> result = callMcpTool(
                    "/tools/getRewardPoints?customerId=" + request.customerId(), null);
            mcpCallsMade.add("getRewardPoints");

            String responseMessage = String.format(
                    "You have %s reward points valued at INR %s. %s",
                    result.getOrDefault("totalPoints", "0"),
                    result.getOrDefault("valueInRupees", "0"),
                    result.containsKey("expiryDate")
                            ? "Points expiring on " + result.get("expiryDate") + "."
                            : "");

            return new CardAgentResponse(
                    request.sessionId(),
                    responseMessage,
                    Map.of("totalPoints", String.valueOf(result.getOrDefault("totalPoints", "0")),
                            "valueInRupees", String.valueOf(result.getOrDefault("valueInRupees", "0"))),
                    mcpCallsMade,
                    "TIER_0"
            );

        } catch (Exception e) {
            log.error("Failed to fetch reward points: customerId={}", request.customerId(), e);
            return new CardAgentResponse(
                    request.sessionId(),
                    "I'm unable to retrieve your reward points at the moment. Please try again later.",
                    Map.of(),
                    mcpCallsMade,
                    "TIER_0"
            );
        }
    }

    // ── Tier 2: Dispute Raise (LLM-assisted) ──

    private CardAgentResponse processDisputeRaise(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            String userPrompt = buildUserPrompt(request);

            String llmResponse = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            // If we have enough context from the request, also call MCP
            if (request.customerContext() != null && request.customerContext().containsKey("transactionId")) {
                Map<String, Object> mcpRequest = Map.of(
                        "customerId", request.customerId(),
                        "transactionId", request.customerContext().get("transactionId"),
                        "reason", request.message() != null ? request.message() : "Customer dispute",
                        "amount", request.customerContext().getOrDefault("amount", "0")
                );

                Map<String, Object> result = callMcpTool("/tools/raiseDispute", mcpRequest);
                mcpCallsMade.add("raiseDispute");

                return new CardAgentResponse(
                        request.sessionId(),
                        MaskingUtils.maskCardNumber(llmResponse) + " Dispute ID: "
                                + result.getOrDefault("disputeId", "N/A"),
                        Map.of("disputeId", String.valueOf(result.getOrDefault("disputeId", "N/A"))),
                        mcpCallsMade,
                        "TIER_2"
                );
            }

            return new CardAgentResponse(
                    request.sessionId(),
                    MaskingUtils.maskCardNumber(llmResponse),
                    Map.of(),
                    mcpCallsMade,
                    "TIER_2"
            );

        } catch (Exception e) {
            log.error("Error processing dispute query: customerId={}", request.customerId(), e);
            return new CardAgentResponse(
                    request.sessionId(),
                    "I apologize, but I encountered an issue processing your dispute. Please try again.",
                    Map.of(),
                    mcpCallsMade,
                    "TIER_2"
            );
        }
    }

    // ── Tier 1: Card Activate ──

    private CardAgentResponse processCardActivate(CardAgentRequest request, List<String> mcpCallsMade) {
        String cardLast4 = extractCardLast4(request);

        try {
            Map<String, Object> mcpRequest = Map.of(
                    "customerId", request.customerId(),
                    "cardLast4", cardLast4
            );

            Map<String, Object> result = callMcpTool("/tools/activateCard", mcpRequest);
            mcpCallsMade.add("activateCard");

            String responseMessage = String.format(
                    "Your card ending in %s has been activated successfully. Activated at: %s. "
                            + "You can now use your card for transactions.",
                    cardLast4, result.getOrDefault("activatedAt", "now"));

            return new CardAgentResponse(
                    request.sessionId(),
                    responseMessage,
                    Map.of("cardLast4", cardLast4, "status",
                            String.valueOf(result.getOrDefault("status", "ACTIVATED"))),
                    mcpCallsMade,
                    "TIER_1"
            );

        } catch (Exception e) {
            log.error("Failed to activate card: customerId={}, cardLast4=****{}", request.customerId(), cardLast4, e);
            return new CardAgentResponse(
                    request.sessionId(),
                    "We encountered an issue activating your card. Please try again or visit a branch.",
                    Map.of("cardLast4", cardLast4),
                    mcpCallsMade,
                    "TIER_1"
            );
        }
    }

    // ── Tier 2: EMI Convert (LLM-assisted) ──

    private CardAgentResponse processEmiConvert(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            String userPrompt = buildUserPrompt(request);

            String llmResponse = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            // If we have transaction details, call MCP to convert
            if (request.customerContext() != null && request.customerContext().containsKey("transactionId")) {
                String tenure = request.customerContext().getOrDefault("tenure", "12");
                Map<String, Object> mcpRequest = Map.of(
                        "customerId", request.customerId(),
                        "transactionId", request.customerContext().get("transactionId"),
                        "tenure", tenure
                );

                Map<String, Object> result = callMcpTool("/tools/convertToEMI", mcpRequest);
                mcpCallsMade.add("convertToEMI");

                String emiDetails = String.format(
                        "EMI conversion successful. Monthly EMI: INR %s, Interest rate: %s%%, "
                                + "Total cost: INR %s, EMI ID: %s.",
                        result.getOrDefault("emiAmount", "N/A"),
                        result.getOrDefault("interestRate", "N/A"),
                        result.getOrDefault("totalCost", "N/A"),
                        result.getOrDefault("emiId", "N/A"));

                return new CardAgentResponse(
                        request.sessionId(),
                        MaskingUtils.maskCardNumber(llmResponse) + " " + emiDetails,
                        Map.of("emiId", String.valueOf(result.getOrDefault("emiId", "N/A")),
                                "emiAmount", String.valueOf(result.getOrDefault("emiAmount", "N/A"))),
                        mcpCallsMade,
                        "TIER_2"
                );
            }

            return new CardAgentResponse(
                    request.sessionId(),
                    MaskingUtils.maskCardNumber(llmResponse),
                    Map.of(),
                    mcpCallsMade,
                    "TIER_2"
            );

        } catch (Exception e) {
            log.error("Error processing EMI conversion: customerId={}", request.customerId(), e);
            return new CardAgentResponse(
                    request.sessionId(),
                    "I apologize, but I encountered an issue with the EMI conversion. Please try again.",
                    Map.of(),
                    mcpCallsMade,
                    "TIER_2"
            );
        }
    }

    // ── General (LLM-powered) ──

    private CardAgentResponse processGeneral(CardAgentRequest request, List<String> mcpCallsMade) {
        try {
            String userPrompt = buildUserPrompt(request);

            String llmResponse = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            return new CardAgentResponse(
                    request.sessionId(),
                    MaskingUtils.maskCardNumber(llmResponse),
                    Map.of(),
                    mcpCallsMade,
                    "GENERAL"
            );

        } catch (Exception e) {
            log.error("Error processing general card query: customerId={}", request.customerId(), e);
            return new CardAgentResponse(
                    request.sessionId(),
                    "I apologize, but I encountered an issue processing your request. Please try again.",
                    Map.of(),
                    mcpCallsMade,
                    "GENERAL"
            );
        }
    }

    // ── Helper methods ──

    private PolicyDecision evaluateVaultPolicy(String customerId, String intent, String sessionId) {
        return vaultClient.evaluatePolicy(
                "card-agent",
                intent != null ? intent.toLowerCase() : "card_query",
                "customer:" + customerId,
                Map.of(
                        "sessionId", sessionId != null ? sessionId : "",
                        "channel", "orchestrator"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callMcpTool(String toolPath, Map<String, Object> requestBody) {
        String url = cardManagementUrl + toolPath;
        log.debug("Calling MCP tool: {}", toolPath);

        try {
            if (requestBody != null) {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url, HttpMethod.POST, new HttpEntity<>(requestBody),
                        new ParameterizedTypeReference<>() {});
                return response.getBody() != null ? response.getBody() : Map.of();
            } else {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url, HttpMethod.GET, null,
                        new ParameterizedTypeReference<>() {});
                return response.getBody() != null ? response.getBody() : Map.of();
            }
        } catch (RestClientException e) {
            log.error("MCP tool call failed: path={}, error={}", toolPath, e.getMessage(), e);
            throw e;
        }
    }

    private void sendBlockNotification(String customerId, String cardLast4) {
        try {
            Map<String, Object> notification = Map.of(
                    "customerId", customerId,
                    "type", "SMS",
                    "template", "CARD_BLOCKED",
                    "parameters", Map.of("cardLast4", cardLast4)
            );
            restTemplate.postForObject(notificationUrl + "/api/v1/notify", notification, Map.class);
            log.info("Block notification sent for customerId={}, cardLast4=****{}", customerId, cardLast4);
        } catch (Exception e) {
            log.warn("Failed to send block notification for customerId={}: {}", customerId, e.getMessage());
            // Non-critical: don't fail the block operation if notification fails
        }
    }

    private String extractCardLast4(CardAgentRequest request) {
        if (request.customerContext() != null && request.customerContext().containsKey("cardLast4")) {
            return request.customerContext().get("cardLast4");
        }
        return "";
    }

    private String buildUserPrompt(CardAgentRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Customer ID: ").append(request.customerId()).append("\n");
        prompt.append("Customer message: ").append(request.message()).append("\n");

        if (request.intent() != null) {
            prompt.append("Detected intent: ").append(request.intent()).append("\n");
        }
        if (request.language() != null) {
            prompt.append("Preferred language: ").append(request.language()).append("\n");
        }
        if (request.conversationHistory() != null && !request.conversationHistory().isEmpty()) {
            prompt.append("Conversation history:\n");
            for (String entry : request.conversationHistory()) {
                prompt.append("  - ").append(entry).append("\n");
            }
        }
        if (request.customerContext() != null && !request.customerContext().isEmpty()) {
            prompt.append("Customer context: ").append(
                    MaskingUtils.maskCardNumber(request.customerContext().toString())).append("\n");
        }

        prompt.append("\nPlease process this customer's card-related request. ");
        prompt.append("NEVER include full card numbers in your response. ");
        prompt.append("Always refer to cards by their last 4 digits only.");

        return prompt.toString();
    }
}
