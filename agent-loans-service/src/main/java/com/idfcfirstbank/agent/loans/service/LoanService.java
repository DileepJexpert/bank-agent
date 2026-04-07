package com.idfcfirstbank.agent.loans.service;

import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.loans.model.LoanRequest;
import com.idfcfirstbank.agent.loans.model.LoanResponse;
import com.idfcfirstbank.agent.loans.tools.LoanTools;
import com.idfcfirstbank.agent.loans.util.EmiCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main service for the Loan Agent. Orchestrates AI-powered query processing
 * with Spring AI function calling, parallel MCP calls, and policy enforcement via Vault.
 * <p>
 * For each query, the service:
 * <ol>
 *   <li>Checks vault policy to verify the action is permitted</li>
 *   <li>Routes to the appropriate handler based on intent</li>
 *   <li>Publishes an audit event for every operation</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final ChatClient chatClient;
    private final VaultClient vaultClient;
    private final AuditEventPublisher auditEventPublisher;
    private final LoanTools loanTools;

    /**
     * Process a loan query by routing to the appropriate handler based on intent.
     */
    public LoanResponse processQuery(LoanRequest request) {
        long startTime = System.currentTimeMillis();
        String intent = request.intent() != null ? request.intent() : "GENERAL_LOAN_QUERY";

        // Vault policy check before every operation
        String action = mapIntentToAction(intent);
        PolicyDecision decision = vaultClient.evaluatePolicy(
                "loan-agent",
                action,
                "customer:" + request.customerId(),
                Map.of("sessionId", request.sessionId(), "channel", "orchestrator",
                        "intent", intent)
        );

        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault policy denied loan query: customerId={}, intent={}, reason={}",
                    request.customerId(), intent, decision.reason());
            LoanResponse response = new LoanResponse(
                    request.sessionId(),
                    "I'm unable to process this request at the moment. " + decision.reason(),
                    intent,
                    false,
                    false
            );
            publishAuditEvent(request, response, "DENY", startTime);
            return response;
        }

        try {
            LoanResponse response = switch (intent) {
                case "LOAN_ELIGIBILITY" -> handleEligibility(request, decision);
                case "LOAN_EMI_QUERY" -> handleEmiQuery(request);
                case "LOAN_PREPAYMENT" -> handlePrepayment(request, decision);
                default -> handleGeneralQuery(request);
            };

            publishAuditEvent(request, response, decision.decision().name(), startTime);
            return response;

        } catch (Exception e) {
            log.error("Error processing loan query: customerId={}, intent={}",
                    request.customerId(), intent, e);
            LoanResponse errorResponse = new LoanResponse(
                    request.sessionId(),
                    "I apologize, but I encountered an issue processing your request. Please try again.",
                    intent,
                    false,
                    false
            );
            publishAuditEvent(request, errorResponse, "ERROR", startTime);
            return errorResponse;
        }
    }

    /**
     * Handle LOAN_ELIGIBILITY intent.
     * Makes 3 parallel calls to credit-bureau, account-aggregator, and core-banking MCPs.
     * Checks vault policy for INITIATE_LOAN. If ESCALATE (amount > 10L), sets requiresApproval.
     */
    private LoanResponse handleEligibility(LoanRequest request, PolicyDecision initialDecision) {
        Map<String, Object> params = request.parameters();
        double requestedAmount = params.containsKey("amount")
                ? ((Number) params.get("amount")).doubleValue() : 0;
        int requestedTenure = params.containsKey("tenure")
                ? ((Number) params.get("tenure")).intValue() : 240;

        // 3 parallel MCP calls using CompletableFuture
        CompletableFuture<String> creditBureauFuture = CompletableFuture.supplyAsync(() -> {
            log.info("Parallel call: credit-bureau for customerId={}", request.customerId());
            return loanTools.checkEligibilityRaw(request.customerId(), requestedAmount, requestedTenure);
        });

        CompletableFuture<String> accountAggregatorFuture = CompletableFuture.supplyAsync(() -> {
            log.info("Parallel call: account-aggregator for customerId={}", request.customerId());
            return loanTools.getLoanDetailsRaw(request.customerId(), "");
        });

        CompletableFuture<String> coreBankingFuture = CompletableFuture.supplyAsync(() -> {
            log.info("Parallel call: core-banking for customerId={}", request.customerId());
            return loanTools.getRepaymentScheduleRaw(request.customerId(), "");
        });

        // Wait for all 3 to complete
        CompletableFuture.allOf(creditBureauFuture, accountAggregatorFuture, coreBankingFuture).join();

        String creditData = creditBureauFuture.join();
        String aaData = accountAggregatorFuture.join();
        String cbData = coreBankingFuture.join();

        // Check vault policy for loan initiation
        PolicyDecision loanDecision = vaultClient.evaluatePolicy(
                "loan-agent",
                "INITIATE_LOAN",
                "customer:" + request.customerId(),
                Map.of("amount", requestedAmount, "tenure", requestedTenure)
        );

        boolean requiresApproval = false;
        if (loanDecision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Loan requires approval: customerId={}, amount={}, reason={}",
                    request.customerId(), requestedAmount, loanDecision.reason());
            requiresApproval = true;
        }

        if (loanDecision.decision() == PolicyDecision.Decision.DENY) {
            return new LoanResponse(
                    request.sessionId(),
                    "Loan eligibility check was not approved. " + loanDecision.reason(),
                    "LOAN_ELIGIBILITY",
                    false,
                    false
            );
        }

        // Combine data and generate loan offer
        StringBuilder offerBuilder = new StringBuilder();
        offerBuilder.append("Loan Eligibility Assessment:\n\n");
        offerBuilder.append("Credit Bureau Data:\n").append(creditData).append("\n\n");
        offerBuilder.append("Financial Profile:\n").append(aaData).append("\n\n");
        offerBuilder.append("Banking Relationship:\n").append(cbData).append("\n\n");

        if (requestedAmount > 0) {
            // Calculate EMIs for common tenures
            double assumedRate = 8.5;
            int[] tenures = {12, 24, 36, 60, 120, 240};
            offerBuilder.append("EMI Options for requested amount of INR ")
                    .append(String.format("%.2f", requestedAmount)).append(":\n");
            for (int tenure : tenures) {
                if (tenure <= requestedTenure || requestedTenure == 0) {
                    double emi = EmiCalculator.calculateEMI(requestedAmount, assumedRate, tenure);
                    offerBuilder.append(String.format("  %d months: INR %.2f/month\n", tenure, emi));
                }
            }
        }

        if (requiresApproval) {
            offerBuilder.append("\nNote: This loan amount requires manager approval before disbursement.");
        }

        return new LoanResponse(
                request.sessionId(),
                offerBuilder.toString(),
                "LOAN_ELIGIBILITY",
                false,
                requiresApproval
        );
    }

    /**
     * Handle LOAN_EMI_QUERY intent.
     * Pure Java calculation, no LLM needed (Tier 0).
     */
    private LoanResponse handleEmiQuery(LoanRequest request) {
        Map<String, Object> params = request.parameters();

        // If loan details are provided, calculate EMI directly
        if (params.containsKey("principal") && params.containsKey("rate") && params.containsKey("tenure")) {
            double principal = ((Number) params.get("principal")).doubleValue();
            double rate = ((Number) params.get("rate")).doubleValue();
            int tenure = ((Number) params.get("tenure")).intValue();

            double emi = EmiCalculator.calculateEMI(principal, rate, tenure);
            double totalPayment = emi * tenure;
            double totalInterest = totalPayment - principal;

            String message = String.format(
                    "EMI Calculation Results:\n\n" +
                    "Principal: INR %.2f\n" +
                    "Annual Interest Rate: %.2f%%\n" +
                    "Tenure: %d months\n\n" +
                    "Monthly EMI: INR %.2f\n" +
                    "Total Payment: INR %.2f\n" +
                    "Total Interest: INR %.2f",
                    principal, rate, tenure, emi, totalPayment, totalInterest);

            return new LoanResponse(request.sessionId(), message, "LOAN_EMI_QUERY", false, false);
        }

        // If loanId is provided, fetch loan details and calculate remaining EMIs
        if (params.containsKey("loanId")) {
            String loanId = params.get("loanId").toString();
            String loanDetails = loanTools.getLoanDetailsRaw(request.customerId(), loanId);

            String message = String.format(
                    "Loan Details for %s:\n\n%s\n\n" +
                    "Use the loan details above to view your remaining EMI schedule.",
                    loanId, loanDetails);

            return new LoanResponse(request.sessionId(), message, "LOAN_EMI_QUERY", false, false);
        }

        return new LoanResponse(
                request.sessionId(),
                "Please provide either loan details (principal, rate, tenure) for a new EMI calculation, " +
                "or your loan ID to view existing loan EMI details.",
                "LOAN_EMI_QUERY",
                false,
                false
        );
    }

    /**
     * Handle LOAN_PREPAYMENT intent.
     * Gets loan details, calculates 2 scenarios, checks prepayment charges.
     */
    private LoanResponse handlePrepayment(LoanRequest request, PolicyDecision initialDecision) {
        Map<String, Object> params = request.parameters();

        String loanId = params.containsKey("loanId") ? params.get("loanId").toString() : "";
        double prepayAmount = params.containsKey("prepaymentAmount")
                ? ((Number) params.get("prepaymentAmount")).doubleValue() : 0;

        if (loanId.isEmpty() || prepayAmount <= 0) {
            return new LoanResponse(
                    request.sessionId(),
                    "Please provide your loan ID and the prepayment amount to calculate the impact.",
                    "LOAN_PREPAYMENT",
                    false,
                    false
            );
        }

        // Get current loan details
        String loanDetails = loanTools.getLoanDetailsRaw(request.customerId(), loanId);

        // Extract loan parameters from details (use defaults if parsing fails)
        double outstanding = params.containsKey("outstanding")
                ? ((Number) params.get("outstanding")).doubleValue() : 1000000;
        double annualRate = params.containsKey("rate")
                ? ((Number) params.get("rate")).doubleValue() : 8.5;
        int remainingMonths = params.containsKey("remainingMonths")
                ? ((Number) params.get("remainingMonths")).intValue() : 120;
        boolean isFloatingRate = params.containsKey("floatingRate")
                ? Boolean.parseBoolean(params.get("floatingRate").toString()) : true;

        // Calculate prepayment impact (2 scenarios)
        Map<String, Object> impact = EmiCalculator.prepaymentImpact(
                outstanding, prepayAmount, annualRate, remainingMonths);

        // Prepayment charges: 0% for floating rate per RBI mandate
        double prepaymentCharges = isFloatingRate ? 0.0 : prepayAmount * 0.02;

        StringBuilder message = new StringBuilder();
        message.append("Prepayment Analysis for Loan ").append(loanId).append(":\n\n");
        message.append("Current Loan Details:\n");
        message.append(loanDetails).append("\n\n");
        message.append(String.format("Outstanding: INR %.2f\n", outstanding));
        message.append(String.format("Prepayment Amount: INR %.2f\n", prepayAmount));
        message.append(String.format("Interest Rate: %.2f%% (%s)\n\n",
                annualRate, isFloatingRate ? "Floating" : "Fixed"));

        message.append("Option 1 - Reduced EMI (same tenure):\n");
        message.append(String.format("  Current EMI: INR %.2f\n", impact.get("originalEMI")));
        message.append(String.format("  New EMI: INR %.2f\n", impact.get("newEMI")));
        message.append(String.format("  Interest Saved: INR %.2f\n\n", impact.get("savedInterest")));

        message.append("Option 2 - Reduced Tenure (same EMI):\n");
        message.append(String.format("  EMI: INR %.2f\n", impact.get("originalEMI")));
        message.append(String.format("  New Tenure: %d months (reduced by %d months)\n",
                impact.get("newTenure"), impact.get("tenureReduction")));
        message.append(String.format("  Interest Saved: INR %.2f\n\n", impact.get("savedInterestReducedTenure")));

        message.append(String.format("Prepayment Charges: INR %.2f", prepaymentCharges));
        if (isFloatingRate) {
            message.append(" (Zero charges for floating rate loans as per RBI guidelines)");
        }

        return new LoanResponse(
                request.sessionId(),
                message.toString(),
                "LOAN_PREPAYMENT",
                false,
                false
        );
    }

    /**
     * Handle general loan queries using ChatClient with function calling.
     */
    private LoanResponse handleGeneralQuery(LoanRequest request) {
        String userPrompt = buildUserPrompt(request);

        String response = chatClient.prompt()
                .user(userPrompt)
                .functions(
                        "checkEligibility",
                        "getLoanDetails",
                        "calculateEmi",
                        "calculatePrepayment",
                        "getRepaymentSchedule"
                )
                .call()
                .content();

        return new LoanResponse(
                request.sessionId(),
                response,
                request.intent(),
                false,
                false
        );
    }

    private String buildUserPrompt(LoanRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Customer ID: ").append(request.customerId()).append("\n");
        prompt.append("Customer message: ").append(request.message()).append("\n");

        if (request.intent() != null) {
            prompt.append("Detected intent: ").append(request.intent()).append("\n");
        }
        if (request.parameters() != null && !request.parameters().isEmpty()) {
            prompt.append("Extracted parameters: ").append(request.parameters()).append("\n");
        }

        prompt.append("\nPlease process this customer's loan-related request using the available tools. ");
        prompt.append("Provide a clear, friendly response with the relevant information. ");
        prompt.append("Format all currency amounts in INR.");

        return prompt.toString();
    }

    private String mapIntentToAction(String intent) {
        return switch (intent) {
            case "LOAN_ELIGIBILITY" -> "CHECK_ELIGIBILITY";
            case "LOAN_EMI_QUERY" -> "QUERY_EMI";
            case "LOAN_PREPAYMENT" -> "CALCULATE_PREPAYMENT";
            default -> "LOAN_QUERY";
        };
    }

    private void publishAuditEvent(LoanRequest request, LoanResponse response,
                                    String policyResult, long startTime) {
        try {
            long latencyMs = System.currentTimeMillis() - startTime;
            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "loan-agent",
                    request.sessionId(),
                    request.customerId(),
                    request.intent() != null ? request.intent() : "GENERAL_LOAN_QUERY",
                    "customer:" + request.customerId(),
                    policyResult,
                    request.message(),
                    response.message(),
                    latencyMs
            );
            auditEventPublisher.publish(event);
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", e.getMessage());
        }
    }
}
