package com.idfcfirstbank.agent.mcp.corebanking.tools;

import com.idfcfirstbank.agent.mcp.corebanking.client.FinacleClient;
import com.idfcfirstbank.agent.mcp.corebanking.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP tool definitions exposed as REST endpoints.
 * <p>
 * Each endpoint acts as an MCP-compatible tool that can be invoked by upstream
 * agent services. The tools delegate to the {@link FinacleClient} for actual
 * core banking operations with resilience patterns applied.
 * <p>
 * Tools exposed:
 * <ul>
 *   <li>getBalance - Retrieve account balance</li>
 *   <li>getTransactionHistory - Fetch transaction history</li>
 *   <li>initiateTransfer - Execute a fund transfer</li>
 *   <li>createFD - Create a fixed deposit</li>
 *   <li>getAccountDetails - Retrieve full account information</li>
 *   <li>getInterestCertificate - Generate interest certificate</li>
 *   <li>getMiniStatement - Get last N transactions</li>
 *   <li>requestChequeBook - Request a new cheque book</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/core-banking")
@RequiredArgsConstructor
@Tag(name = "Core Banking Tools", description = "MCP-compatible core banking tool endpoints")
public class CoreBankingTools {

    private final FinacleClient finacleClient;

    /**
     * Tool: getBalance - Retrieve account balance.
     */
    @PostMapping("/balance")
    @Operation(summary = "Get account balance", description = "MCP tool: Retrieves current and available balance")
    public ResponseEntity<AccountBalance> getBalance(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String accountNumber = request.getOrDefault("accountNumber", "");

        log.info("MCP Tool: getBalance - customerId={}, accountNumber={}", customerId, accountNumber);

        AccountBalance balance = finacleClient.getBalance(customerId, accountNumber);
        return ResponseEntity.ok(balance);
    }

    /**
     * Tool: getTransactionHistory - Fetch transaction history for an account.
     */
    @PostMapping("/transactions")
    @Operation(summary = "Get transaction history", description = "MCP tool: Retrieves transaction history for a date range")
    public ResponseEntity<TransactionHistory> getTransactionHistory(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String accountNumber = request.getOrDefault("accountNumber", "");
        String fromDate = request.getOrDefault("fromDate", "");
        String toDate = request.getOrDefault("toDate", "");

        log.info("MCP Tool: getTransactionHistory - customerId={}, accountNumber={}", customerId, accountNumber);

        TransactionHistory history = finacleClient.getTransactionHistory(customerId, accountNumber, fromDate, toDate);
        return ResponseEntity.ok(history);
    }

    /**
     * Tool: initiateTransfer - Execute a fund transfer.
     */
    @PostMapping("/transfer")
    @Operation(summary = "Initiate fund transfer", description = "MCP tool: Initiates a fund transfer between accounts")
    public ResponseEntity<TransferRequest.TransferResponse> initiateTransfer(
            @Valid @RequestBody TransferRequest request) {
        log.info("MCP Tool: initiateTransfer - from={}, to={}, amount={}",
                request.fromAccount(), request.toAccount(), request.amount());

        TransferRequest.TransferResponse response = finacleClient.initiateTransfer(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Tool: createFD - Create a new fixed deposit.
     */
    @PostMapping("/fd/create")
    @Operation(summary = "Create fixed deposit", description = "MCP tool: Creates a new fixed deposit")
    public ResponseEntity<FDCreateRequest.FDCreateResponse> createFD(@Valid @RequestBody FDCreateRequest request) {
        log.info("MCP Tool: createFD - customerId={}, amount={}, tenure={}",
                request.customerId(), request.amount(), request.tenureMonths());

        FDCreateRequest.FDCreateResponse response = finacleClient.createFixedDeposit(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Tool: getAccountDetails - Retrieve full account information.
     */
    @PostMapping("/account-details")
    @Operation(summary = "Get account details", description = "MCP tool: Retrieves comprehensive account information")
    public ResponseEntity<AccountDetails> getAccountDetails(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String accountNumber = request.getOrDefault("accountNumber", "");

        log.info("MCP Tool: getAccountDetails - customerId={}, accountNumber={}", customerId, accountNumber);

        AccountDetails details = finacleClient.getAccountDetails(customerId, accountNumber);
        return ResponseEntity.ok(details);
    }

    /**
     * Tool: getInterestCertificate - Generate interest certificate.
     */
    @PostMapping("/interest-certificate")
    @Operation(summary = "Get interest certificate", description = "MCP tool: Generates interest certificate for a financial year")
    public ResponseEntity<Map<String, String>> getInterestCertificate(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String accountNumber = request.getOrDefault("accountNumber", "");
        String financialYear = request.getOrDefault("financialYear", "2024-25");

        log.info("MCP Tool: getInterestCertificate - customerId={}, year={}", customerId, financialYear);

        String certificate = finacleClient.getInterestCertificate(customerId, accountNumber, financialYear);
        return ResponseEntity.ok(Map.of("certificate", certificate));
    }

    /**
     * Tool: getMiniStatement - Get the last 10 transactions.
     */
    @PostMapping("/mini-statement")
    @Operation(summary = "Get mini statement", description = "MCP tool: Retrieves last 10 transactions")
    public ResponseEntity<TransactionHistory> getMiniStatement(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String accountNumber = request.getOrDefault("accountNumber", "");

        log.info("MCP Tool: getMiniStatement - customerId={}, accountNumber={}", customerId, accountNumber);

        TransactionHistory statement = finacleClient.getMiniStatement(customerId, accountNumber);
        return ResponseEntity.ok(statement);
    }

    /**
     * Tool: requestChequeBook - Request a new cheque book.
     */
    @PostMapping("/cheque-book/request")
    @Operation(summary = "Request cheque book", description = "MCP tool: Submits a cheque book request")
    public ResponseEntity<Map<String, String>> requestChequeBook(@RequestBody Map<String, Object> request) {
        String customerId = (String) request.getOrDefault("customerId", "");
        String accountNumber = (String) request.getOrDefault("accountNumber", "");
        int numberOfLeaves = request.containsKey("numberOfLeaves")
                ? ((Number) request.get("numberOfLeaves")).intValue()
                : 25;

        log.info("MCP Tool: requestChequeBook - customerId={}, leaves={}", customerId, numberOfLeaves);

        String result = finacleClient.requestChequeBook(customerId, accountNumber, numberOfLeaves);
        return ResponseEntity.ok(Map.of("message", result));
    }
}
