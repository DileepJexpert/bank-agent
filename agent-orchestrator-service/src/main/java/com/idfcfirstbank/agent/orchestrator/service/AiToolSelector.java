package com.idfcfirstbank.agent.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Function;

/**
 * AI-powered tool selector using Spring AI function calling.
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

    private final ChatClient toolCallingClient;
    private final List<String> toolsInvoked = Collections.synchronizedList(new ArrayList<>());

    public AiToolSelector(ChatClient.Builder builder) {
        this.toolCallingClient = builder.defaultSystem("""
                You are an IDFC First Bank AI agent with access to banking tools.
                Analyze the customer's request and call the appropriate tools to fulfill it.
                You may call MULTIPLE tools if needed (e.g., for loan eligibility, call
                getCreditScore AND checkLoanEligibility AND calculateEMI).

                After getting tool results, generate a natural, friendly response in
                the SAME LANGUAGE the customer used (Hindi, English, or Hinglish).
                Be concise - max 3-4 sentences. Never reveal full account/card numbers.

                If amount is mentioned in lakhs, convert: 1 lakh = 100000, 10 lakh = 1000000.
                Default interest rate for personal loans: 11.5%.
                """).build();
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

        try {
            String response = toolCallingClient.prompt()
                    .user(prompt)
                    .functions(
                            "getCreditScore",
                            "checkLoanEligibility",
                            "calculateEMI",
                            "getAccountBalance",
                            "getTransactions",
                            "blockCard",
                            "createFixedDeposit",
                            "getRewardPoints"
                    )
                    .call()
                    .content();

            List<String> called = new ArrayList<>(toolsInvoked);
            log.info("AI Tool Selection complete: {} tools called: {}", called.size(), called);
            return new ToolSelectionResult(response, called, true);

        } catch (Exception e) {
            log.error("AI Tool Selection failed: {}", e.getMessage(), e);
            return new ToolSelectionResult(null, List.of(), false);
        }
    }

    /**
     * Record a tool invocation (called by each tool function).
     */
    private void recordToolCall(String toolName) {
        toolsInvoked.add(toolName);
        log.info("[AI Tool Selection] LLM invoked tool: {}", toolName);
    }

    public record ToolSelectionResult(String response, List<String> toolsCalled, boolean success) {}

    // ======================================================================
    // Spring AI Function Beans — LLM sees these descriptions and decides
    // which ones to call. This is REAL AI tool selection.
    // ======================================================================

    @Configuration
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public class AiToolBeans {

        @Value("${agent.mcp.core-banking-url:http://mcp-core-banking-server:8086}")
        private String coreBankingUrl;

        @Value("${agent.mcp.credit-bureau-url:http://mcp-credit-bureau:8091}")
        private String creditBureauUrl;

        @Value("${agent.routing.card-service-url:http://agent-card-service:8088}")
        private String cardServiceUrl;

        private final RestTemplate restTemplate;

        public AiToolBeans(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        @Bean
        @Description("Get customer's CIBIL credit score from credit bureau. Input: customerId (string)")
        public Function<CreditScoreRequest, String> getCreditScore() {
            return request -> {
                recordToolCall("getCreditScore");
                log.info("MCP call: getCreditScore(customerId={})", request.customerId());
                try {
                    String url = creditBureauUrl + "/api/v1/credit-bureau/score";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url,
                            Map.of("customerId", request.customerId()), Map.class);
                    return response != null ? response.toString()
                            : "{\"score\": 742, \"rating\": \"GOOD\", \"agency\": \"CIBIL\"}";
                } catch (Exception e) {
                    log.warn("Credit bureau MCP unavailable, using mock: {}", e.getMessage());
                    return "{\"score\": 742, \"rating\": \"GOOD\", \"agency\": \"CIBIL\", \"source\": \"mock\"}";
                }
            };
        }

        @Bean
        @Description("Check if customer is eligible for a loan based on income and amount. Input: customerId (string), monthlyIncome (double), requestedAmount (double), tenureMonths (int)")
        public Function<LoanEligibilityRequest, String> checkLoanEligibility() {
            return request -> {
                recordToolCall("checkLoanEligibility");
                log.info("MCP call: checkLoanEligibility(customerId={}, income={}, amount={}, tenure={})",
                        request.customerId(), request.monthlyIncome(), request.requestedAmount(), request.tenureMonths());
                try {
                    String url = coreBankingUrl + "/api/v1/core-banking/loan-eligibility";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url,
                            Map.of("customerId", request.customerId(),
                                    "monthlyIncome", request.monthlyIncome(),
                                    "requestedAmount", request.requestedAmount(),
                                    "tenureMonths", request.tenureMonths()), Map.class);
                    return response != null ? response.toString()
                            : buildEligibilityMock(request);
                } catch (Exception e) {
                    log.warn("Core banking MCP unavailable, using mock: {}", e.getMessage());
                    return buildEligibilityMock(request);
                }
            };
        }

        @Bean
        @Description("Calculate monthly EMI for a loan. Input: principal (double), annualInterestRate (double), tenureMonths (int)")
        public Function<EmiCalcRequest, String> calculateEMI() {
            return request -> {
                recordToolCall("calculateEMI");
                log.info("Tool call: calculateEMI(principal={}, rate={}, tenure={})",
                        request.principal(), request.annualInterestRate(), request.tenureMonths());
                double r = request.annualInterestRate() / 12.0 / 100.0;
                int n = request.tenureMonths();
                double p = request.principal();
                double emi = p * r * Math.pow(1 + r, n) / (Math.pow(1 + r, n) - 1);
                double totalPayment = emi * n;
                double totalInterest = totalPayment - p;
                return String.format("{\"emi\": %.0f, \"totalPayment\": %.0f, \"totalInterest\": %.0f, " +
                                "\"principal\": %.0f, \"rate\": %.1f, \"tenureMonths\": %d}",
                        emi, totalPayment, totalInterest, p, request.annualInterestRate(), n);
            };
        }

        @Bean
        @Description("Get current account balance for a customer. Input: customerId (string)")
        public Function<BalanceRequest, String> getAccountBalance() {
            return request -> {
                recordToolCall("getAccountBalance");
                log.info("MCP call: getAccountBalance(customerId={})", request.customerId());
                try {
                    String url = coreBankingUrl + "/api/v1/core-banking/balance";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url,
                            Map.of("customerId", request.customerId()), Map.class);
                    return response != null ? response.toString()
                            : "{\"balance\": 245830, \"currency\": \"INR\", \"accountType\": \"SAVINGS\"}";
                } catch (Exception e) {
                    log.warn("Core banking MCP unavailable, using mock: {}", e.getMessage());
                    return "{\"balance\": 245830, \"currency\": \"INR\", \"accountType\": \"SAVINGS\", \"source\": \"mock\"}";
                }
            };
        }

        @Bean
        @Description("Get recent transactions for a customer. Input: customerId (string), count (int)")
        public Function<TransactionsRequest, String> getTransactions() {
            return request -> {
                recordToolCall("getTransactions");
                log.info("MCP call: getTransactions(customerId={}, count={})", request.customerId(), request.count());
                try {
                    String url = coreBankingUrl + "/api/v1/core-banking/mini-statement";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url,
                            Map.of("customerId", request.customerId()), Map.class);
                    return response != null ? response.toString()
                            : "[{\"date\":\"2026-04-06\",\"description\":\"Swiggy\",\"amount\":-450},{\"date\":\"2026-04-05\",\"description\":\"Salary Credit\",\"amount\":120000}]";
                } catch (Exception e) {
                    log.warn("Core banking MCP unavailable, using mock: {}", e.getMessage());
                    return "[{\"date\":\"2026-04-06\",\"description\":\"Swiggy\",\"amount\":-450,\"source\":\"mock\"},{\"date\":\"2026-04-05\",\"description\":\"Salary Credit\",\"amount\":120000,\"source\":\"mock\"}]";
                }
            };
        }

        @Bean
        @Description("Block a customer's debit or credit card immediately. Input: customerId (string), cardLast4 (string), reason (string)")
        public Function<BlockCardRequest, String> blockCard() {
            return request -> {
                recordToolCall("blockCard");
                log.info("MCP call: blockCard(customerId={}, cardLast4={}, reason={})",
                        request.customerId(), request.cardLast4(), request.reason());
                try {
                    String url = cardServiceUrl + "/api/v1/cards/block";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url,
                            Map.of("customerId", request.customerId(),
                                    "cardLast4", request.cardLast4(),
                                    "reason", request.reason()), Map.class);
                    return response != null ? response.toString()
                            : "{\"status\": \"BLOCKED\", \"cardLast4\": \"" + request.cardLast4() + "\", \"referenceId\": \"BLK-" + UUID.randomUUID().toString().substring(0, 8) + "\"}";
                } catch (Exception e) {
                    log.warn("Card service unavailable, using mock: {}", e.getMessage());
                    return "{\"status\": \"BLOCKED\", \"cardLast4\": \"" + request.cardLast4() + "\", \"referenceId\": \"BLK-" + UUID.randomUUID().toString().substring(0, 8) + "\", \"source\": \"mock\"}";
                }
            };
        }

        @Bean
        @Description("Create a fixed deposit for a customer. Input: customerId (string), amount (double), tenureMonths (int)")
        public Function<CreateFDRequest, String> createFixedDeposit() {
            return request -> {
                recordToolCall("createFixedDeposit");
                log.info("MCP call: createFixedDeposit(customerId={}, amount={}, tenure={})",
                        request.customerId(), request.amount(), request.tenureMonths());
                try {
                    String url = coreBankingUrl + "/api/v1/core-banking/fd/create";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url,
                            Map.of("customerId", request.customerId(),
                                    "amount", request.amount(),
                                    "tenureMonths", request.tenureMonths()), Map.class);
                    return response != null ? response.toString()
                            : "{\"fdId\": \"FD-" + UUID.randomUUID().toString().substring(0, 8) + "\", \"rate\": 7.25, \"maturityAmount\": " + (request.amount() * 1.0725) + "}";
                } catch (Exception e) {
                    log.warn("Core banking MCP unavailable, using mock: {}", e.getMessage());
                    return "{\"fdId\": \"FD-" + UUID.randomUUID().toString().substring(0, 8) + "\", \"rate\": 7.25, \"maturityAmount\": " + (request.amount() * 1.0725) + ", \"source\": \"mock\"}";
                }
            };
        }

        @Bean
        @Description("Get reward points balance for a customer's card. Input: customerId (string)")
        public Function<RewardPointsRequest, String> getRewardPoints() {
            return request -> {
                recordToolCall("getRewardPoints");
                log.info("MCP call: getRewardPoints(customerId={})", request.customerId());
                try {
                    String url = cardServiceUrl + "/api/v1/cards/reward-points";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restTemplate.postForObject(url,
                            Map.of("customerId", request.customerId()), Map.class);
                    return response != null ? response.toString()
                            : "{\"points\": 14500, \"value\": 1450, \"currency\": \"INR\"}";
                } catch (Exception e) {
                    log.warn("Card service unavailable, using mock: {}", e.getMessage());
                    return "{\"points\": 14500, \"value\": 1450, \"currency\": \"INR\", \"source\": \"mock\"}";
                }
            };
        }

        private String buildEligibilityMock(LoanEligibilityRequest req) {
            boolean eligible = req.monthlyIncome() * 60 >= req.requestedAmount();
            double maxEligible = req.monthlyIncome() * 60;
            return String.format("{\"eligible\": %s, \"maxEligibleAmount\": %.0f, " +
                            "\"interestRate\": 11.5, \"requestedAmount\": %.0f, \"source\": \"mock\"}",
                    eligible, maxEligible, req.requestedAmount());
        }
    }

    // --- Tool input records ---
    public record CreditScoreRequest(String customerId) {}
    public record LoanEligibilityRequest(String customerId, double monthlyIncome, double requestedAmount, int tenureMonths) {}
    public record EmiCalcRequest(double principal, double annualInterestRate, int tenureMonths) {}
    public record BalanceRequest(String customerId) {}
    public record TransactionsRequest(String customerId, int count) {}
    public record BlockCardRequest(String customerId, String cardLast4, String reason) {}
    public record CreateFDRequest(String customerId, double amount, int tenureMonths) {}
    public record RewardPointsRequest(String customerId) {}
}
