package com.idfcfirstbank.agent.wealth.service;

import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.wealth.model.PortfolioSummary;
import com.idfcfirstbank.agent.wealth.model.WealthRequest;
import com.idfcfirstbank.agent.wealth.model.WealthResponse;
import com.idfcfirstbank.agent.wealth.tools.WealthTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main service for the Wealth Agent. Orchestrates AI-powered query processing
 * with Spring AI function calling and policy enforcement via Vault.
 * <p>
 * Processes wealth-related queries by switching on intent:
 * <ul>
 *   <li>PORTFOLIO_SUMMARY (Tier 1): Fetch MF holdings, FDs, insurance from MCP servers</li>
 *   <li>SIP_MANAGEMENT (Tier 1): Create/modify/cancel SIP via investment MCP</li>
 *   <li>INVESTMENT_QUERY (Tier 2 - LLM): Use ChatClient with SEBI disclaimer</li>
 *   <li>INSURANCE_STATUS (Tier 0): Fetch policy status, premium due date</li>
 * </ul>
 * <p>
 * Vault policy check is enforced before every operation. AuditEvent is published after every operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WealthService {

    /**
     * SEBI-mandated disclaimer that MUST be included before ANY investment recommendation
     * or mutual fund related information.
     */
    public static final String SEBI_DISCLAIMER =
            "Mutual fund investments are subject to market risks. Read all scheme related documents carefully. "
                    + "Past performance is not indicative of future returns.";

    private static final String AGENT_ID = "wealth-agent";

    private final ChatClient chatClient;
    private final VaultClient vaultClient;
    private final WealthTools wealthTools;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${agent.instance-id:${HOSTNAME:wealth-agent-0}}")
    private String instanceId;

    /**
     * Process a wealth query by routing to the appropriate handler based on intent.
     */
    public WealthResponse processQuery(WealthRequest request) {
        long startTime = System.currentTimeMillis();
        String action = request.intent() != null ? request.intent() : "wealth_query";

        // Vault policy check before every operation
        PolicyDecision decision = vaultClient.evaluatePolicy(
                AGENT_ID,
                action,
                "customer:" + request.customerId(),
                Map.of("sessionId", request.sessionId(), "channel", "orchestrator")
        );

        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault policy denied wealth query: customerId={}, intent={}, reason={}",
                    request.customerId(), request.intent(), decision.reason());
            WealthResponse response = new WealthResponse(
                    request.sessionId(),
                    "I'm unable to process this request at the moment. " + decision.reason(),
                    request.intent(),
                    false,
                    null
            );
            publishAuditEvent(request, response, decision, startTime);
            return response;
        }

        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Vault policy requires escalation: customerId={}, intent={}",
                    request.customerId(), request.intent());
            WealthResponse response = new WealthResponse(
                    request.sessionId(),
                    "This request requires additional verification. Connecting you to a wealth specialist.",
                    request.intent(),
                    true,
                    null
            );
            publishAuditEvent(request, response, decision, startTime);
            return response;
        }

        // Route to handler based on intent
        WealthResponse response;
        try {
            response = switch (action.toUpperCase()) {
                case "PORTFOLIO_SUMMARY" -> handlePortfolioSummary(request);
                case "SIP_MANAGEMENT" -> handleSipManagement(request);
                case "INVESTMENT_QUERY" -> handleInvestmentQuery(request);
                case "INSURANCE_STATUS" -> handleInsuranceStatus(request);
                default -> handleInvestmentQuery(request); // fallback to LLM for unrecognized intents
            };
        } catch (Exception e) {
            log.error("Error processing wealth query: customerId={}, intent={}",
                    request.customerId(), request.intent(), e);
            response = new WealthResponse(
                    request.sessionId(),
                    "I apologize, but I encountered an issue processing your request. Please try again.",
                    request.intent(),
                    false,
                    null
            );
        }

        publishAuditEvent(request, response, decision, startTime);
        return response;
    }

    /**
     * Tier 1: Portfolio summary. Fetches MF holdings, FDs, and insurance from MCP servers.
     * Returns a formatted summary without LLM involvement.
     */
    public WealthResponse handlePortfolioSummary(WealthRequest request) {
        log.info("Handling PORTFOLIO_SUMMARY for customerId={}", request.customerId());

        String portfolioData = wealthTools.getPortfolioRaw(request.customerId());

        StringBuilder sb = new StringBuilder();
        sb.append(SEBI_DISCLAIMER).append("\n\n");
        sb.append("Portfolio Summary for Customer ").append(request.customerId()).append("\n");
        sb.append("As of: ").append(LocalDate.now()).append("\n\n");
        sb.append(portfolioData);

        return new WealthResponse(
                request.sessionId(),
                sb.toString(),
                "PORTFOLIO_SUMMARY",
                false,
                SEBI_DISCLAIMER
        );
    }

    /**
     * Tier 1: SIP management. Create, modify, or cancel SIPs via the investment MCP server.
     * Determines the sub-action from request parameters.
     */
    public WealthResponse handleSipManagement(WealthRequest request) {
        log.info("Handling SIP_MANAGEMENT for customerId={}", request.customerId());

        Map<String, Object> params = request.parameters();
        String subAction = params.getOrDefault("action", "view").toString().toUpperCase();

        String result;
        switch (subAction) {
            case "CREATE" -> {
                String fundName = params.getOrDefault("fundName", "").toString();
                double amount = params.containsKey("amount")
                        ? Double.parseDouble(params.get("amount").toString()) : 0.0;
                String frequency = params.getOrDefault("frequency", "MONTHLY").toString();
                String startDate = params.getOrDefault("startDate", "").toString();
                result = wealthTools.createSipRaw(request.customerId(), fundName, amount, frequency, startDate);
            }
            case "MODIFY" -> {
                String sipId = params.getOrDefault("sipId", "").toString();
                double newAmount = params.containsKey("newAmount")
                        ? Double.parseDouble(params.get("newAmount").toString()) : 0.0;
                String newFrequency = params.getOrDefault("newFrequency", "").toString();
                result = wealthTools.modifySipRaw(request.customerId(), sipId, newAmount, newFrequency);
            }
            case "CANCEL" -> {
                String sipId = params.getOrDefault("sipId", "").toString();
                String reason = params.getOrDefault("reason", "").toString();
                result = wealthTools.cancelSipRaw(request.customerId(), sipId, reason);
            }
            default -> {
                result = wealthTools.getSipDetailsRaw(request.customerId());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(SEBI_DISCLAIMER).append("\n\n");
        sb.append(result);

        return new WealthResponse(
                request.sessionId(),
                sb.toString(),
                "SIP_MANAGEMENT",
                false,
                SEBI_DISCLAIMER
        );
    }

    /**
     * Tier 2 (LLM): Investment query. Uses ChatClient with function calling.
     * MUST include SEBI disclaimer. Checks risk profile before providing advice.
     */
    public WealthResponse handleInvestmentQuery(WealthRequest request) {
        log.info("Handling INVESTMENT_QUERY for customerId={}", request.customerId());

        // Check risk profile first
        String riskProfile = wealthTools.getRiskProfileRaw(request.customerId());

        try {
            String userPrompt = buildInvestmentPrompt(request, riskProfile);

            String llmResponse = chatClient.prompt()
                    .user(userPrompt)
                    .functions(
                            "getPortfolio",
                            "getSipDetails",
                            "createSip",
                            "modifySip",
                            "cancelSip",
                            "getInsuranceStatus",
                            "getRiskProfile"
                    )
                    .call()
                    .content();

            // Ensure SEBI disclaimer is always present in response
            String fullResponse = SEBI_DISCLAIMER + "\n\n" + llmResponse;

            return new WealthResponse(
                    request.sessionId(),
                    fullResponse,
                    "INVESTMENT_QUERY",
                    false,
                    SEBI_DISCLAIMER
            );

        } catch (Exception e) {
            log.error("LLM processing failed for investment query: customerId={}",
                    request.customerId(), e);
            return new WealthResponse(
                    request.sessionId(),
                    "I apologize, but I encountered an issue processing your investment query. Please try again.",
                    "INVESTMENT_QUERY",
                    false,
                    null
            );
        }
    }

    /**
     * Tier 0: Insurance status. Fetches policy status and premium due date.
     * Pure Java formatting, no LLM needed.
     */
    public WealthResponse handleInsuranceStatus(WealthRequest request) {
        log.info("Handling INSURANCE_STATUS for customerId={}", request.customerId());

        String policyNumber = request.parameters().getOrDefault("policyNumber", "").toString();
        String insuranceData = wealthTools.getInsuranceStatusRaw(request.customerId(), policyNumber);

        StringBuilder sb = new StringBuilder();
        sb.append("Insurance Status for Customer ").append(request.customerId()).append("\n\n");
        sb.append(insuranceData);

        return new WealthResponse(
                request.sessionId(),
                sb.toString(),
                "INSURANCE_STATUS",
                false,
                null
        );
    }

    /**
     * Build a prompt for the LLM that includes risk profile context and the SEBI disclaimer requirement.
     */
    private String buildInvestmentPrompt(WealthRequest request, String riskProfile) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Customer ID: ").append(request.customerId()).append("\n");
        prompt.append("Customer message: ").append(request.message()).append("\n");
        prompt.append("Customer Risk Profile: ").append(riskProfile).append("\n");

        if (request.intent() != null) {
            prompt.append("Detected intent: ").append(request.intent()).append("\n");
        }
        if (request.parameters() != null && !request.parameters().isEmpty()) {
            prompt.append("Extracted parameters: ").append(request.parameters()).append("\n");
        }

        prompt.append("\nIMPORTANT: You MUST consider the customer's risk profile before making ");
        prompt.append("any investment recommendations. Never suggest products that exceed the ");
        prompt.append("customer's risk tolerance.\n");
        prompt.append("Process this customer's wealth management request using the available tools. ");
        prompt.append("Provide a clear, friendly response with the relevant financial information.");

        return prompt.toString();
    }

    /**
     * Publish an audit event to Kafka after every operation.
     */
    private void publishAuditEvent(WealthRequest request, WealthResponse response,
                                   PolicyDecision decision, long startTime) {
        try {
            long latencyMs = System.currentTimeMillis() - startTime;
            String requestPayload = sanitizeForAudit(request);
            String responsePayload = sanitizeForAudit(response);

            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    AGENT_ID,
                    instanceId,
                    request.customerId(),
                    request.intent() != null ? request.intent() : "wealth_query",
                    "customer:" + request.customerId(),
                    decision.decision().name(),
                    requestPayload,
                    responsePayload,
                    latencyMs
            );

            auditEventPublisher.publish(event);
        } catch (Exception e) {
            log.error("Failed to publish audit event for customerId={}: {}",
                    request.customerId(), e.getMessage());
        }
    }

    /**
     * Sanitize payloads for audit logging (remove sensitive data).
     */
    private String sanitizeForAudit(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }
}
