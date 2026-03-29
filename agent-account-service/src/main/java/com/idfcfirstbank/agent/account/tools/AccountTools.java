package com.idfcfirstbank.agent.account.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI function callbacks (tools) for account operations.
 * <p>
 * Each {@code @Bean} method returns a {@link Function} that Spring AI can invoke
 * during LLM tool-calling. The functions act as a bridge between the LLM and
 * the MCP Core Banking Server.
 */
@Slf4j
@Configuration
public class AccountTools {

    @Value("${agent.mcp.core-banking-url:http://mcp-core-banking-server:8086}")
    private String coreBankingUrl;

    private final RestTemplate restTemplate;

    public AccountTools(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // --- Spring AI Function Beans ---

    @Bean
    @Description("Get the current balance for a customer's bank account. Input: customerId (string), accountNumber (string)")
    public Function<BalanceRequest, String> getBalance() {
        return request -> {
            log.info("Tool call: getBalance(customerId={}, accountNumber={})",
                    request.customerId(), request.accountNumber());
            return getBalanceRaw(request.customerId(), request.accountNumber());
        };
    }

    @Bean
    @Description("Get transaction history for a customer's account. Input: customerId (string), accountNumber (string), fromDate (string, optional), toDate (string, optional)")
    public Function<TransactionHistoryRequest, String> getTransactionHistory() {
        return request -> {
            log.info("Tool call: getTransactionHistory(customerId={}, accountNumber={})",
                    request.customerId(), request.accountNumber());
            return getTransactionHistoryRaw(
                    request.customerId(), request.accountNumber(),
                    request.fromDate(), request.toDate());
        };
    }

    @Bean
    @Description("Get detailed account information for a customer. Input: customerId (string), accountNumber (string)")
    public Function<AccountDetailsRequest, String> getAccountDetails() {
        return request -> {
            log.info("Tool call: getAccountDetails(customerId={}, accountNumber={})",
                    request.customerId(), request.accountNumber());
            return getAccountDetailsRaw(request.customerId(), request.accountNumber());
        };
    }

    @Bean
    @Description("Request a new cheque book for the customer's account. Input: customerId (string), accountNumber (string), numberOfLeaves (int)")
    public Function<ChequeBookRequest, String> requestChequeBook() {
        return request -> {
            log.info("Tool call: requestChequeBook(customerId={}, accountNumber={}, leaves={})",
                    request.customerId(), request.accountNumber(), request.numberOfLeaves());
            return requestChequeBookRaw(request.customerId(), request.accountNumber(), request.numberOfLeaves());
        };
    }

    @Bean
    @Description("Get a mini statement with the last 10 transactions. Input: customerId (string), accountNumber (string)")
    public Function<MiniStatementRequest, String> getMiniStatement() {
        return request -> {
            log.info("Tool call: getMiniStatement(customerId={}, accountNumber={})",
                    request.customerId(), request.accountNumber());
            return getMiniStatementRaw(request.customerId(), request.accountNumber());
        };
    }

    // --- Raw methods used by both tool beans and direct Tier 0 calls ---

    @SuppressWarnings("unchecked")
    public String getBalanceRaw(String customerId, String accountNumber) {
        try {
            String url = coreBankingUrl + "/api/v1/core-banking/balance";
            Map<String, String> body = Map.of("customerId", customerId, "accountNumber", accountNumber);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Balance information unavailable";
        } catch (Exception e) {
            log.error("Failed to get balance from MCP core banking: {}", e.getMessage());
            return "Unable to retrieve balance at this time. Please try again later.";
        }
    }

    @SuppressWarnings("unchecked")
    public String getTransactionHistoryRaw(String customerId, String accountNumber,
                                           String fromDate, String toDate) {
        try {
            String url = coreBankingUrl + "/api/v1/core-banking/transactions";
            Map<String, String> body = Map.of(
                    "customerId", customerId,
                    "accountNumber", accountNumber,
                    "fromDate", fromDate != null ? fromDate : "",
                    "toDate", toDate != null ? toDate : ""
            );
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Transaction history unavailable";
        } catch (Exception e) {
            log.error("Failed to get transaction history from MCP core banking: {}", e.getMessage());
            return "Unable to retrieve transaction history at this time.";
        }
    }

    @SuppressWarnings("unchecked")
    public String getAccountDetailsRaw(String customerId, String accountNumber) {
        try {
            String url = coreBankingUrl + "/api/v1/core-banking/account-details";
            Map<String, String> body = Map.of("customerId", customerId, "accountNumber", accountNumber);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Account details unavailable";
        } catch (Exception e) {
            log.error("Failed to get account details from MCP core banking: {}", e.getMessage());
            return "Unable to retrieve account details at this time.";
        }
    }

    @SuppressWarnings("unchecked")
    public String requestChequeBookRaw(String customerId, String accountNumber, int leaves) {
        try {
            String url = coreBankingUrl + "/api/v1/core-banking/cheque-book/request";
            Map<String, Object> body = Map.of(
                    "customerId", customerId,
                    "accountNumber", accountNumber,
                    "numberOfLeaves", leaves
            );
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Cheque book request submitted";
        } catch (Exception e) {
            log.error("Failed to request cheque book from MCP core banking: {}", e.getMessage());
            return "Unable to process cheque book request at this time.";
        }
    }

    @SuppressWarnings("unchecked")
    public String getMiniStatementRaw(String customerId, String accountNumber) {
        try {
            String url = coreBankingUrl + "/api/v1/core-banking/mini-statement";
            Map<String, String> body = Map.of("customerId", customerId, "accountNumber", accountNumber);
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "Mini statement unavailable";
        } catch (Exception e) {
            log.error("Failed to get mini statement from MCP core banking: {}", e.getMessage());
            return "Unable to retrieve mini statement at this time.";
        }
    }

    // --- Tool input records ---

    public record BalanceRequest(String customerId, String accountNumber) {}
    public record TransactionHistoryRequest(String customerId, String accountNumber, String fromDate, String toDate) {}
    public record AccountDetailsRequest(String customerId, String accountNumber) {}
    public record ChequeBookRequest(String customerId, String accountNumber, int numberOfLeaves) {}
    public record MiniStatementRequest(String customerId, String accountNumber) {}
}
