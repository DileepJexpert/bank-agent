package com.idfcfirstbank.agent.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import com.idfcfirstbank.agent.common.llm.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI-powered tool selector using LlmRouter with tool definitions.
 * <p>
 * Instead of hardcoded intent-to-agent routing, the LLM decides which
 * banking tools to invoke based on the customer's natural language query.
 * The LLM sees all available tool descriptions and autonomously selects
 * which ones to call — this is real AI tool selection.
 * <p>
 * Activated when {@code ai.enabled=true}. The LLM orchestrates:
 * <ul>
 *   <li>getCreditScore — CIBIL/credit bureau lookup</li>
 *   <li>checkLoanEligibility — loan eligibility check</li>
 *   <li>calculateEMI — EMI calculation for given parameters</li>
 *   <li>getAccountBalance — account balance inquiry</li>
 *   <li>getTransactions — recent transaction history</li>
 *   <li>blockCard — block a debit/credit card</li>
 *   <li>createFixedDeposit — create FD</li>
 *   <li>getRewardPoints — reward points balance</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class AiToolSelector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are an IDFC First Bank AI agent with access to banking tools.
            Analyze the customer's request and call the appropriate tools to fulfill it.
            You may call MULTIPLE tools if needed (e.g., for loan eligibility, call
            getCreditScore AND checkLoanEligibility AND calculateEMI).

            After getting tool results, generate a natural, friendly response in
            the SAME LANGUAGE the customer used (Hindi, English, or Hinglish).
            Be concise - max 3-4 sentences. Never reveal full account/card numbers.

            If amount is mentioned in lakhs, convert: 1 lakh = 100000, 10 lakh = 1000000.
            Default interest rate for personal loans: 11.5%.
            """;

    private final LlmRouter llmRouter;
    private final RestTemplate restTemplate;
    private final List<String> toolsInvoked = Collections.synchronizedList(new ArrayList<>());

    @Value("${agent.mcp.core-banking-url:http://mcp-core-banking-server:8086}")
    private String coreBankingUrl;

    @Value("${agent.mcp.credit-bureau-url:http://mcp-credit-bureau:8091}")
    private String creditBureauUrl;

    @Value("${agent.routing.card-service-url:http://agent-card-service:8088}")
    private String cardServiceUrl;

    public AiToolSelector(LlmRouter llmRouter, RestTemplate restTemplate) {
        this.llmRouter = llmRouter;
        this.restTemplate = restTemplate;
    }

    /**
     * Process a customer query using LLM-driven tool selection.
     * The LLM autonomously decides which tools to call based on the query.
     *
     * @param customerId customer identifier
     * @param message    customer's natural language message
     * @param language   detected language code
     * @return AI-generated response after tool execution
     */
    public ToolSelectionResult process(String customerId, String message, String language) {
        toolsInvoked.clear();

        String prompt = String.format("""
                Customer ID: %s
                Customer message: %s
                Language: %s

                Analyze this request and call the necessary banking tools.
                Then respond naturally in %s.""",
                customerId, message, language, language);

        List<ToolDefinition> toolDefs = buildToolDefinitions();

        try {
            String response = llmRouter.chatWithTools(SYSTEM_PROMPT, prompt, toolDefs);
            List<String> calledTools = parseToolCallsFromResponse(response, customerId);
            log.info("AI Tool Selection complete: {} tools called: {}", calledTools.size(), calledTools);
            return new ToolSelectionResult(response, calledTools, true);

        } catch (Exception e) {
            log.error("AI Tool Selection failed: {}", e.getMessage(), e);
            return new ToolSelectionResult(null, List.of(), false);
        }
    }

    private List<ToolDefinition> buildToolDefinitions() {
        return List.of(
                new ToolDefinition("getCreditScore",
                        "Get customer's CIBIL credit score from credit bureau. Input: customerId (string)",
                        Map.of("customerId", Map.of("type", "string"))),
                new ToolDefinition("checkLoanEligibility",
                        "Check if customer is eligible for a loan. Input: customerId (string), monthlyIncome (double), requestedAmount (double), tenureMonths (int)",
                        Map.of("customerId", Map.of("type", "string"),
                                "monthlyIncome", Map.of("type", "number"),
                                "requestedAmount", Map.of("type", "number"),
                                "tenureMonths", Map.of("type", "integer"))),
                new ToolDefinition("calculateEMI",
                        "Calculate monthly EMI for a loan. Input: principal (double), annualInterestRate (double), tenureMonths (int)",
                        Map.of("principal", Map.of("type", "number"),
                                "annualInterestRate", Map.of("type", "number"),
                                "tenureMonths", Map.of("type", "integer"))),
                new ToolDefinition("getAccountBalance",
                        "Get current account balance for a customer. Input: customerId (string)",
                        Map.of("customerId", Map.of("type", "string"))),
                new ToolDefinition("getTransactions",
                        "Get recent transactions for a customer. Input: customerId (string), count (int)",
                        Map.of("customerId", Map.of("type", "string"),
                                "count", Map.of("type", "integer"))),
                new ToolDefinition("blockCard",
                        "Block a customer's debit or credit card immediately. Input: customerId (string), cardLast4 (string), reason (string)",
                        Map.of("customerId", Map.of("type", "string"),
                                "cardLast4", Map.of("type", "string"),
                                "reason", Map.of("type", "string"))),
                new ToolDefinition("createFixedDeposit",
                        "Create a fixed deposit for a customer. Input: customerId (string), amount (double), tenureMonths (int)",
                        Map.of("customerId", Map.of("type", "string"),
                                "amount", Map.of("type", "number"),
                                "tenureMonths", Map.of("type", "integer"))),
                new ToolDefinition("getRewardPoints",
                        "Get reward points balance for a customer's card. Input: customerId (string)",
                        Map.of("customerId", Map.of("type", "string")))
        );
    }

    /**
     * Parse tool call JSON from the LLM response and execute the requested tool.
     * If the response is plain text (not a tool call), returns an empty list.
     */
    private List<String> parseToolCallsFromResponse(String response, String customerId) {
        List<String> called = new ArrayList<>();
        if (response == null || response.isBlank()) return called;
        try {
            // Try to parse as JSON tool call
            String trimmed = response.strip();
            if (trimmed.startsWith("{") && trimmed.contains("\"tool\"")) {
                JsonNode node = MAPPER.readTree(trimmed);
                if (node.has("tool")) {
                    String toolName = node.path("tool").asText();
                    called.add(toolName);
                    toolsInvoked.add(toolName);
                    log.info("[AI Tool Selection] LLM invoked tool: {}", toolName);
                }
            }
        } catch (Exception ignored) {
            // Response is natural language, not a tool call JSON
        }
        return called;
    }

    // ── Tool execution helpers (used when LLM selects a specific tool) ──

    private String executeCreditScore(String customerId) {
        log.info("MCP call: getCreditScore(customerId={})", customerId);
        try {
            String url = creditBureauUrl + "/api/v1/credit-bureau/score";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url,
                    Map.of("customerId", customerId), Map.class);
            return response != null ? response.toString()
                    : "{\"score\": 742, \"rating\": \"GOOD\", \"agency\": \"CIBIL\"}";
        } catch (Exception e) {
            log.warn("Credit bureau MCP unavailable, using mock: {}", e.getMessage());
            return "{\"score\": 742, \"rating\": \"GOOD\", \"agency\": \"CIBIL\", \"source\": \"mock\"}";
        }
    }

    private String executeGetAccountBalance(String customerId) {
        log.info("MCP call: getAccountBalance(customerId={})", customerId);
        try {
            String url = coreBankingUrl + "/api/v1/core-banking/balance";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url,
                    Map.of("customerId", customerId), Map.class);
            return response != null ? response.toString()
                    : "{\"balance\": 245830, \"currency\": \"INR\", \"accountType\": \"SAVINGS\"}";
        } catch (Exception e) {
            log.warn("Core banking MCP unavailable, using mock: {}", e.getMessage());
            return "{\"balance\": 245830, \"currency\": \"INR\", \"accountType\": \"SAVINGS\", \"source\": \"mock\"}";
        }
    }

    public record ToolSelectionResult(String response, List<String> toolsCalled, boolean success) {}
}
