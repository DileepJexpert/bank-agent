package com.idfcfirstbank.agent.mcp.corebanking.client;

import com.idfcfirstbank.agent.mcp.corebanking.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Client for the Finacle core banking system.
 * <p>
 * Applies resilience patterns via Resilience4j annotations:
 * <ul>
 *   <li>{@code @CircuitBreaker} - Prevents cascading failures when Finacle is down</li>
 *   <li>{@code @Retry} - Retries transient failures</li>
 *   <li>{@code @RateLimiter} - Protects Finacle from excessive load</li>
 * </ul>
 * <p>
 * Currently returns mock data. When Finacle integration is ready, replace the
 * mock implementations with actual WebClient calls to {@code finacleBaseUrl}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinacleClient {

    private final WebClient finacleWebClient;

    // ----- Balance -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "getBalanceFallback")
    @Retry(name = "finacle")
    @RateLimiter(name = "finacle")
    @Cacheable(value = "accountBalance", key = "#customerId + ':' + #accountNumber", unless = "#result == null")
    public AccountBalance getBalance(String customerId, String accountNumber) {
        log.info("Fetching balance from Finacle: customerId={}, accountNumber={}", customerId, accountNumber);

        // TODO: Replace with actual Finacle API call
        // return finacleWebClient.get()
        //         .uri("/api/accounts/{accountNumber}/balance", accountNumber)
        //         .retrieve()
        //         .bodyToMono(AccountBalance.class)
        //         .block();

        // Mock response
        return new AccountBalance(
                accountNumber.isEmpty() ? "1234567890" : accountNumber,
                customerId,
                "SAVINGS",
                new BigDecimal("125750.50"),
                new BigDecimal("123250.50"),
                "INR",
                LocalDateTime.now()
        );
    }

    @SuppressWarnings("unused")
    private AccountBalance getBalanceFallback(String customerId, String accountNumber, Throwable t) {
        log.error("Finacle getBalance circuit breaker fallback: customerId={}, error={}", customerId, t.getMessage());
        return new AccountBalance(accountNumber, customerId, "UNKNOWN",
                BigDecimal.ZERO, BigDecimal.ZERO, "INR", LocalDateTime.now());
    }

    // ----- Transaction History -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "getTransactionHistoryFallback")
    @Retry(name = "finacle")
    @RateLimiter(name = "finacle")
    public TransactionHistory getTransactionHistory(String customerId, String accountNumber,
                                                    String fromDate, String toDate) {
        log.info("Fetching transaction history from Finacle: customerId={}, accountNumber={}", customerId, accountNumber);

        // Mock response
        List<TransactionHistory.Transaction> transactions = List.of(
                new TransactionHistory.Transaction(
                        "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        LocalDateTime.now().minusDays(1),
                        "UPI/PAYMENT/SWIGGY",
                        new BigDecimal("450.00"), "DEBIT",
                        new BigDecimal("125300.50"), "UPI123456"),
                new TransactionHistory.Transaction(
                        "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        LocalDateTime.now().minusDays(2),
                        "NEFT/SALARY/ACME CORP",
                        new BigDecimal("85000.00"), "CREDIT",
                        new BigDecimal("125750.50"), "NEFT789012"),
                new TransactionHistory.Transaction(
                        "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        LocalDateTime.now().minusDays(3),
                        "ATM/CASH WITHDRAWAL",
                        new BigDecimal("5000.00"), "DEBIT",
                        new BigDecimal("40750.50"), "ATM345678"),
                new TransactionHistory.Transaction(
                        "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        LocalDateTime.now().minusDays(5),
                        "IMPS/TRANSFER/JOHN DOE",
                        new BigDecimal("2500.00"), "DEBIT",
                        new BigDecimal("45750.50"), "IMPS901234"),
                new TransactionHistory.Transaction(
                        "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        LocalDateTime.now().minusDays(7),
                        "BILL PAYMENT/ELECTRICITY/TATA POWER",
                        new BigDecimal("3200.00"), "DEBIT",
                        new BigDecimal("48250.50"), "BILL567890")
        );

        return new TransactionHistory(
                accountNumber.isEmpty() ? "1234567890" : accountNumber,
                customerId,
                transactions,
                transactions.size()
        );
    }

    @SuppressWarnings("unused")
    private TransactionHistory getTransactionHistoryFallback(String customerId, String accountNumber,
                                                             String fromDate, String toDate, Throwable t) {
        log.error("Finacle getTransactionHistory circuit breaker fallback: {}", t.getMessage());
        return new TransactionHistory(accountNumber, customerId, List.of(), 0);
    }

    // ----- Account Details -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "getAccountDetailsFallback")
    @Retry(name = "finacle")
    @RateLimiter(name = "finacle")
    @Cacheable(value = "accountDetails", key = "#customerId + ':' + #accountNumber", unless = "#result == null")
    public AccountDetails getAccountDetails(String customerId, String accountNumber) {
        log.info("Fetching account details from Finacle: customerId={}", customerId);

        // Mock response
        return new AccountDetails(
                accountNumber.isEmpty() ? "1234567890" : accountNumber,
                customerId,
                "Rahul Sharma",
                "SAVINGS",
                "ACTIVE",
                "INR",
                "IDFC First Bank",
                "Koramangala Branch, Bangalore",
                "IDFB0040001",
                LocalDateTime.of(2020, 3, 15, 10, 0),
                "rahul.sharma@email.com",
                "+91-98765XXXXX"
        );
    }

    @SuppressWarnings("unused")
    private AccountDetails getAccountDetailsFallback(String customerId, String accountNumber, Throwable t) {
        log.error("Finacle getAccountDetails circuit breaker fallback: {}", t.getMessage());
        return new AccountDetails(accountNumber, customerId, "Unknown", "UNKNOWN", "UNKNOWN",
                "INR", "IDFC First Bank", "Unknown", "Unknown", LocalDateTime.now(), "", "");
    }

    // ----- Fund Transfer -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "initiateTransferFallback")
    @Retry(name = "finacle", fallbackMethod = "initiateTransferFallback")
    @RateLimiter(name = "finacle")
    public TransferRequest.TransferResponse initiateTransfer(TransferRequest request) {
        log.info("Initiating transfer via Finacle: from={}, to={}, amount={}",
                request.fromAccount(), request.toAccount(), request.amount());

        // Mock response
        String referenceNumber = "TRF" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return new TransferRequest.TransferResponse(
                referenceNumber,
                "SUCCESS",
                "Fund transfer of INR " + request.amount() + " initiated successfully",
                LocalDateTime.now()
        );
    }

    @SuppressWarnings("unused")
    private TransferRequest.TransferResponse initiateTransferFallback(TransferRequest request, Throwable t) {
        log.error("Finacle initiateTransfer circuit breaker fallback: {}", t.getMessage());
        return new TransferRequest.TransferResponse(
                null, "FAILED", "Transfer service temporarily unavailable", LocalDateTime.now());
    }

    // ----- Fixed Deposit -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "createFDFallback")
    @Retry(name = "finacle")
    @RateLimiter(name = "finacle")
    public FDCreateRequest.FDCreateResponse createFixedDeposit(FDCreateRequest request) {
        log.info("Creating FD via Finacle: customerId={}, amount={}, tenureMonths={}",
                request.customerId(), request.amount(), request.tenureMonths());

        // Mock response
        String fdNumber = "FD" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        return new FDCreateRequest.FDCreateResponse(
                fdNumber,
                "ACTIVE",
                request.amount(),
                new BigDecimal("7.25"),
                request.tenureMonths(),
                LocalDateTime.now(),
                LocalDateTime.now().plusMonths(request.tenureMonths()),
                request.amount().multiply(new BigDecimal("1.0725"))
        );
    }

    @SuppressWarnings("unused")
    private FDCreateRequest.FDCreateResponse createFDFallback(FDCreateRequest request, Throwable t) {
        log.error("Finacle createFD circuit breaker fallback: {}", t.getMessage());
        return new FDCreateRequest.FDCreateResponse(
                null, "FAILED", BigDecimal.ZERO, BigDecimal.ZERO, 0,
                LocalDateTime.now(), LocalDateTime.now(), BigDecimal.ZERO);
    }

    // ----- Cheque Book -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "requestChequeBookFallback")
    @Retry(name = "finacle")
    @RateLimiter(name = "finacle")
    public String requestChequeBook(String customerId, String accountNumber, int numberOfLeaves) {
        log.info("Requesting cheque book via Finacle: customerId={}, leaves={}", customerId, numberOfLeaves);

        // Mock response
        String requestId = "CHQ" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "Cheque book request submitted successfully. Request ID: " + requestId
                + ". " + numberOfLeaves + " leaves cheque book will be delivered to your registered address within 7 business days.";
    }

    @SuppressWarnings("unused")
    private String requestChequeBookFallback(String customerId, String accountNumber, int numberOfLeaves, Throwable t) {
        log.error("Finacle requestChequeBook circuit breaker fallback: {}", t.getMessage());
        return "Cheque book request service temporarily unavailable. Please try again later.";
    }

    // ----- Mini Statement -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "getMiniStatementFallback")
    @Retry(name = "finacle")
    @RateLimiter(name = "finacle")
    public TransactionHistory getMiniStatement(String customerId, String accountNumber) {
        log.info("Fetching mini statement from Finacle: customerId={}", customerId);
        return getTransactionHistory(customerId, accountNumber, null, null);
    }

    @SuppressWarnings("unused")
    private TransactionHistory getMiniStatementFallback(String customerId, String accountNumber, Throwable t) {
        log.error("Finacle getMiniStatement circuit breaker fallback: {}", t.getMessage());
        return new TransactionHistory(accountNumber, customerId, List.of(), 0);
    }

    // ----- Interest Certificate -----

    @CircuitBreaker(name = "finacle", fallbackMethod = "getInterestCertificateFallback")
    @Retry(name = "finacle")
    @RateLimiter(name = "finacle")
    public String getInterestCertificate(String customerId, String accountNumber, String financialYear) {
        log.info("Fetching interest certificate from Finacle: customerId={}, year={}", customerId, financialYear);

        // Mock response
        return "Interest Certificate for FY " + financialYear + "\n"
                + "Account: " + (accountNumber.isEmpty() ? "1234567890" : accountNumber) + "\n"
                + "Total Interest Earned: INR 8,450.00\n"
                + "TDS Deducted: INR 845.00\n"
                + "Net Interest Credited: INR 7,605.00";
    }

    @SuppressWarnings("unused")
    private String getInterestCertificateFallback(String customerId, String accountNumber,
                                                  String financialYear, Throwable t) {
        log.error("Finacle getInterestCertificate circuit breaker fallback: {}", t.getMessage());
        return "Interest certificate service temporarily unavailable.";
    }
}
