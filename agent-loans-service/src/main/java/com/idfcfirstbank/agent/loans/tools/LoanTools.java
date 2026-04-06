package com.idfcfirstbank.agent.loans.tools;

import com.idfcfirstbank.agent.loans.util.EmiCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI function callbacks (tools) for loan operations.
 * <p>
 * Each {@code @Bean} method returns a {@link Function} that Spring AI can invoke
 * during LLM tool-calling. The functions act as a bridge between the LLM and
 * the MCP servers (credit bureau, account aggregator, loan origination, core banking).
 */
@Slf4j
@Configuration
public class LoanTools {

    @Value("${agent.mcp.credit-bureau-url:http://mcp-credit-bureau:8091}")
    private String creditBureauUrl;

    @Value("${agent.mcp.account-aggregator-url:http://mcp-account-aggregator:8092}")
    private String accountAggregatorUrl;

    @Value("${agent.mcp.loan-origination-url:http://mcp-loan-origination:8093}")
    private String loanOriginationUrl;

    @Value("${agent.mcp.core-banking-url:http://mcp-core-banking-server:8086}")
    private String coreBankingUrl;

    private final RestTemplate restTemplate;

    public LoanTools(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // --- Spring AI Function Beans ---

    @Bean
    @Description("Check loan eligibility for a customer using credit bureau, account aggregator, and core banking data. Input: customerId (string), requestedAmount (double), requestedTenure (int)")
    public Function<EligibilityRequest, String> checkEligibility() {
        return request -> {
            log.info("Tool call: checkEligibility(customerId={}, amount={}, tenure={})",
                    request.customerId(), request.requestedAmount(), request.requestedTenure());
            return checkEligibilityRaw(request.customerId(), request.requestedAmount(), request.requestedTenure());
        };
    }

    @Bean
    @Description("Get existing loan details from the loan origination system. Input: customerId (string), loanId (string)")
    public Function<LoanDetailsRequest, String> getLoanDetails() {
        return request -> {
            log.info("Tool call: getLoanDetails(customerId={}, loanId={})",
                    request.customerId(), request.loanId());
            return getLoanDetailsRaw(request.customerId(), request.loanId());
        };
    }

    @Bean
    @Description("Calculate EMI for given principal, annual interest rate, and tenure in months. Input: principal (double), annualRate (double), tenureMonths (int)")
    public Function<EmiRequest, String> calculateEmi() {
        return request -> {
            log.info("Tool call: calculateEmi(principal={}, rate={}, tenure={})",
                    request.principal(), request.annualRate(), request.tenureMonths());
            return calculateEmiRaw(request.principal(), request.annualRate(), request.tenureMonths());
        };
    }

    @Bean
    @Description("Calculate prepayment impact showing reduced EMI and reduced tenure scenarios. Input: outstanding (double), prepayAmount (double), annualRate (double), remainingMonths (int)")
    public Function<PrepaymentRequest, String> calculatePrepayment() {
        return request -> {
            log.info("Tool call: calculatePrepayment(outstanding={}, prepayAmount={}, rate={}, months={})",
                    request.outstanding(), request.prepayAmount(), request.annualRate(), request.remainingMonths());
            return calculatePrepaymentRaw(request.outstanding(), request.prepayAmount(),
                    request.annualRate(), request.remainingMonths());
        };
    }

    @Bean
    @Description("Get the full repayment/amortization schedule for a loan. Input: customerId (string), loanId (string)")
    public Function<RepaymentScheduleRequest, String> getRepaymentSchedule() {
        return request -> {
            log.info("Tool call: getRepaymentSchedule(customerId={}, loanId={})",
                    request.customerId(), request.loanId());
            return getRepaymentScheduleRaw(request.customerId(), request.loanId());
        };
    }

    // --- Raw methods used by both tool beans and direct Tier 0/Tier 2 calls ---

    @SuppressWarnings("unchecked")
    public String checkEligibilityRaw(String customerId, double requestedAmount, int requestedTenure) {
        try {
            // Call credit bureau MCP
            String creditUrl = creditBureauUrl + "/api/v1/credit-bureau/score";
            Map<String, String> creditBody = Map.of("customerId", customerId);
            Map<String, Object> creditResponse = restTemplate.postForObject(creditUrl, creditBody, Map.class);

            // Call account aggregator MCP
            String aaUrl = accountAggregatorUrl + "/api/v1/account-aggregator/financial-profile";
            Map<String, String> aaBody = Map.of("customerId", customerId);
            Map<String, Object> aaResponse = restTemplate.postForObject(aaUrl, aaBody, Map.class);

            // Call core banking MCP for existing relationship data
            String cbUrl = coreBankingUrl + "/api/v1/core-banking/customer-profile";
            Map<String, String> cbBody = Map.of("customerId", customerId);
            Map<String, Object> cbResponse = restTemplate.postForObject(cbUrl, cbBody, Map.class);

            // Combine results
            StringBuilder result = new StringBuilder();
            result.append("Credit Bureau: ").append(creditResponse != null ? creditResponse.toString() : "unavailable");
            result.append("\nAccount Aggregator: ").append(aaResponse != null ? aaResponse.toString() : "unavailable");
            result.append("\nCore Banking: ").append(cbResponse != null ? cbResponse.toString() : "unavailable");
            result.append("\nRequested Amount: ").append(requestedAmount);
            result.append("\nRequested Tenure: ").append(requestedTenure).append(" months");

            return result.toString();
        } catch (Exception e) {
            log.error("Failed to check eligibility for customerId={}: {}", customerId, e.getMessage());
            return "Unable to complete eligibility check at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String getLoanDetailsRaw(String customerId, String loanId) {
        try {
            String url = loanOriginationUrl + "/api/v1/loan-origination/loan-details";
            Map<String, String> body = Map.of("customerId", customerId, "loanId", loanId);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Loan details unavailable";
        } catch (Exception e) {
            log.error("Failed to get loan details from MCP loan origination: {}", e.getMessage());
            return "Unable to retrieve loan details at this time.";
        }
    }

    public String calculateEmiRaw(double principal, double annualRate, int tenureMonths) {
        try {
            double emi = EmiCalculator.calculateEMI(principal, annualRate, tenureMonths);
            double totalPayment = emi * tenureMonths;
            double totalInterest = totalPayment - principal;

            return String.format(
                    "EMI: %.2f, Total Payment: %.2f, Total Interest: %.2f, Principal: %.2f, Rate: %.2f%%, Tenure: %d months",
                    emi, totalPayment, totalInterest, principal, annualRate, tenureMonths);
        } catch (Exception e) {
            log.error("EMI calculation failed: {}", e.getMessage());
            return "Unable to calculate EMI: " + e.getMessage();
        }
    }

    public String calculatePrepaymentRaw(double outstanding, double prepayAmount,
                                          double annualRate, int remainingMonths) {
        try {
            Map<String, Object> impact = EmiCalculator.prepaymentImpact(outstanding, prepayAmount,
                    annualRate, remainingMonths);
            return impact.toString();
        } catch (Exception e) {
            log.error("Prepayment calculation failed: {}", e.getMessage());
            return "Unable to calculate prepayment impact: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    public String getRepaymentScheduleRaw(String customerId, String loanId) {
        try {
            String url = loanOriginationUrl + "/api/v1/loan-origination/repayment-schedule";
            Map<String, String> body = Map.of("customerId", customerId, "loanId", loanId);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Repayment schedule unavailable";
        } catch (Exception e) {
            log.error("Failed to get repayment schedule from MCP loan origination: {}", e.getMessage());
            return "Unable to retrieve repayment schedule at this time.";
        }
    }

    // --- Tool input records ---

    public record EligibilityRequest(String customerId, double requestedAmount, int requestedTenure) {}
    public record LoanDetailsRequest(String customerId, String loanId) {}
    public record EmiRequest(double principal, double annualRate, int tenureMonths) {}
    public record PrepaymentRequest(double outstanding, double prepayAmount, double annualRate, int remainingMonths) {}
    public record RepaymentScheduleRequest(String customerId, String loanId) {}
}
