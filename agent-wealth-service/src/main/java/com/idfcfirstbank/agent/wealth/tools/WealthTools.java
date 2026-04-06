package com.idfcfirstbank.agent.wealth.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI function callbacks (tools) for wealth management operations.
 * <p>
 * Each {@code @Bean} method returns a {@link Function} that Spring AI can invoke
 * during LLM tool-calling. The functions act as a bridge between the LLM and
 * the MCP servers for investment, insurance, and customer profile data.
 */
@Slf4j
@Configuration
public class WealthTools {

    @Value("${agent.mcp.investment-url:http://mcp-investment-server:8090}")
    private String investmentUrl;

    @Value("${agent.mcp.insurance-url:http://mcp-insurance-server:8091}")
    private String insuranceUrl;

    @Value("${agent.mcp.customer-profile-url:http://mcp-customer-profile-server:8092}")
    private String customerProfileUrl;

    private final RestTemplate restTemplate;

    public WealthTools(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // --- Spring AI Function Beans ---

    @Bean
    @Description("Get the complete investment portfolio for a customer including mutual funds, fixed deposits, and insurance. Input: customerId (string)")
    public Function<PortfolioRequest, String> getPortfolio() {
        return request -> {
            log.info("Tool call: getPortfolio(customerId={})", request.customerId());
            return getPortfolioRaw(request.customerId());
        };
    }

    @Bean
    @Description("Get details of all active SIPs (Systematic Investment Plans) for a customer. Input: customerId (string)")
    public Function<SipDetailsRequest, String> getSipDetails() {
        return request -> {
            log.info("Tool call: getSipDetails(customerId={})", request.customerId());
            return getSipDetailsRaw(request.customerId());
        };
    }

    @Bean
    @Description("Create a new Systematic Investment Plan (SIP) for a customer. Input: customerId (string), fundName (string), amount (double), frequency (string), startDate (string)")
    public Function<CreateSipRequest, String> createSip() {
        return request -> {
            log.info("Tool call: createSip(customerId={}, fundName={}, amount={})",
                    request.customerId(), request.fundName(), request.amount());
            return createSipRaw(request.customerId(), request.fundName(),
                    request.amount(), request.frequency(), request.startDate());
        };
    }

    @Bean
    @Description("Modify an existing SIP for a customer. Input: customerId (string), sipId (string), newAmount (double), newFrequency (string)")
    public Function<ModifySipRequest, String> modifySip() {
        return request -> {
            log.info("Tool call: modifySip(customerId={}, sipId={}, newAmount={})",
                    request.customerId(), request.sipId(), request.newAmount());
            return modifySipRaw(request.customerId(), request.sipId(),
                    request.newAmount(), request.newFrequency());
        };
    }

    @Bean
    @Description("Cancel an existing SIP for a customer. Input: customerId (string), sipId (string), reason (string)")
    public Function<CancelSipRequest, String> cancelSip() {
        return request -> {
            log.info("Tool call: cancelSip(customerId={}, sipId={})",
                    request.customerId(), request.sipId());
            return cancelSipRaw(request.customerId(), request.sipId(), request.reason());
        };
    }

    @Bean
    @Description("Get insurance policy status and details for a customer. Input: customerId (string), policyNumber (string, optional)")
    public Function<InsuranceStatusRequest, String> getInsuranceStatus() {
        return request -> {
            log.info("Tool call: getInsuranceStatus(customerId={}, policyNumber={})",
                    request.customerId(), request.policyNumber());
            return getInsuranceStatusRaw(request.customerId(), request.policyNumber());
        };
    }

    @Bean
    @Description("Get the customer's risk profile for investment suitability assessment. Input: customerId (string)")
    public Function<RiskProfileRequest, String> getRiskProfile() {
        return request -> {
            log.info("Tool call: getRiskProfile(customerId={})", request.customerId());
            return getRiskProfileRaw(request.customerId());
        };
    }

    // --- Raw methods used by both tool beans and direct Tier 0/1 calls ---

    @SuppressWarnings("unchecked")
    public String getPortfolioRaw(String customerId) {
        try {
            String url = investmentUrl + "/api/v1/investment/portfolio";
            Map<String, String> body = Map.of("customerId", customerId);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Portfolio information unavailable";
        } catch (Exception e) {
            log.error("Failed to get portfolio from MCP investment server: {}", e.getMessage());
            return "Unable to retrieve portfolio at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String getSipDetailsRaw(String customerId) {
        try {
            String url = investmentUrl + "/api/v1/investment/sip";
            Map<String, String> body = Map.of("customerId", customerId);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "SIP details unavailable";
        } catch (Exception e) {
            log.error("Failed to get SIP details from MCP investment server: {}", e.getMessage());
            return "Unable to retrieve SIP details at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String createSipRaw(String customerId, String fundName, double amount,
                               String frequency, String startDate) {
        try {
            String url = investmentUrl + "/api/v1/investment/sip/create";
            Map<String, Object> body = new HashMap<>();
            body.put("customerId", customerId);
            body.put("fundName", fundName);
            body.put("amount", amount);
            body.put("frequency", frequency != null ? frequency : "MONTHLY");
            body.put("startDate", startDate != null ? startDate : "");
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "SIP creation request submitted";
        } catch (Exception e) {
            log.error("Failed to create SIP via MCP investment server: {}", e.getMessage());
            return "Unable to create SIP at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String modifySipRaw(String customerId, String sipId, double newAmount,
                               String newFrequency) {
        try {
            String url = investmentUrl + "/api/v1/investment/sip/modify";
            Map<String, Object> body = new HashMap<>();
            body.put("customerId", customerId);
            body.put("sipId", sipId);
            body.put("newAmount", newAmount);
            body.put("newFrequency", newFrequency != null ? newFrequency : "");
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "SIP modification request submitted";
        } catch (Exception e) {
            log.error("Failed to modify SIP via MCP investment server: {}", e.getMessage());
            return "Unable to modify SIP at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String cancelSipRaw(String customerId, String sipId, String reason) {
        try {
            String url = investmentUrl + "/api/v1/investment/sip/cancel";
            Map<String, Object> body = new HashMap<>();
            body.put("customerId", customerId);
            body.put("sipId", sipId);
            body.put("reason", reason != null ? reason : "");
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "SIP cancellation request submitted";
        } catch (Exception e) {
            log.error("Failed to cancel SIP via MCP investment server: {}", e.getMessage());
            return "Unable to cancel SIP at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String getInsuranceStatusRaw(String customerId, String policyNumber) {
        try {
            String url = insuranceUrl + "/api/v1/insurance/status";
            Map<String, String> body = new HashMap<>();
            body.put("customerId", customerId);
            body.put("policyNumber", policyNumber != null ? policyNumber : "");
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Insurance status unavailable";
        } catch (Exception e) {
            log.error("Failed to get insurance status from MCP insurance server: {}", e.getMessage());
            return "Unable to retrieve insurance status at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String getRiskProfileRaw(String customerId) {
        try {
            String url = customerProfileUrl + "/api/v1/customer-profile/risk-profile";
            Map<String, String> body = Map.of("customerId", customerId);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Risk profile unavailable";
        } catch (Exception e) {
            log.error("Failed to get risk profile from MCP customer profile server: {}", e.getMessage());
            return "Unable to retrieve risk profile at this time. Please try again later.";
        }
    }

    // --- Tool input records ---

    public record PortfolioRequest(String customerId) {}
    public record SipDetailsRequest(String customerId) {}
    public record CreateSipRequest(String customerId, String fundName, double amount, String frequency, String startDate) {}
    public record ModifySipRequest(String customerId, String sipId, double newAmount, String newFrequency) {}
    public record CancelSipRequest(String customerId, String sipId, String reason) {}
    public record InsuranceStatusRequest(String customerId, String policyNumber) {}
    public record RiskProfileRequest(String customerId) {}
}
