package com.idfcfirstbank.agent.account.controller;

import com.idfcfirstbank.agent.account.model.AccountQueryRequest;
import com.idfcfirstbank.agent.account.model.AccountQueryResponse;
import com.idfcfirstbank.agent.account.model.BalanceResponse;
import com.idfcfirstbank.agent.account.model.StatementRequest;
import com.idfcfirstbank.agent.account.service.AccountAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Account Agent Service.
 * <p>
 * Provides both an AI-powered chat endpoint (for complex queries routed from
 * the orchestrator) and direct Tier 0 endpoints for simple operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account Agent", description = "Account-related banking operations")
public class AccountAgentController {

    private final AccountAgentService accountAgentService;

    /**
     * Handle account-related queries via AI agent with function calling.
     * This endpoint is called by the orchestrator for complex account queries.
     */
    @PostMapping("/chat")
    @Operation(summary = "AI-powered account query", description = "Processes account queries using LLM with function calling")
    public ResponseEntity<AccountQueryResponse> chat(@Valid @RequestBody AccountQueryRequest request) {
        log.info("Account agent chat: customerId={}, intent={}, sessionId={}",
                request.customerId(), request.intent(), request.sessionId());

        AccountQueryResponse response = accountAgentService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Direct balance inquiry endpoint (Tier 0 - no LLM needed).
     */
    @PostMapping("/balance")
    @Operation(summary = "Direct balance inquiry", description = "Tier 0 balance check without LLM")
    public ResponseEntity<BalanceResponse> getBalance(@RequestBody Map<String, String> body) {
        String customerId = body.get("customerId");
        String accountNumber = body.getOrDefault("accountNumber", "");

        log.info("Direct balance inquiry: customerId={}", customerId);

        BalanceResponse balance = accountAgentService.getBalanceDirect(customerId, accountNumber);
        return ResponseEntity.ok(balance);
    }

    /**
     * Request account statement.
     */
    @PostMapping("/statement")
    @Operation(summary = "Request statement", description = "Request account statement for a date range")
    public ResponseEntity<AccountQueryResponse> requestStatement(@Valid @RequestBody StatementRequest request) {
        log.info("Statement request: customerId={}, from={}, to={}",
                request.customerId(), request.fromDate(), request.toDate());

        AccountQueryResponse response = accountAgentService.getStatement(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Request a new cheque book.
     */
    @PostMapping("/cheque/request")
    @Operation(summary = "Request cheque book", description = "Submit a cheque book request")
    public ResponseEntity<AccountQueryResponse> requestChequeBook(@RequestBody Map<String, String> body) {
        String customerId = body.get("customerId");
        String accountNumber = body.getOrDefault("accountNumber", "");
        int leaves = Integer.parseInt(body.getOrDefault("leaves", "25"));

        log.info("Cheque book request: customerId={}, leaves={}", customerId, leaves);

        AccountQueryResponse response = accountAgentService.requestChequeBook(customerId, accountNumber, leaves);
        return ResponseEntity.ok(response);
    }
}
