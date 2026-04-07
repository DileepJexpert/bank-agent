package com.idfcfirstbank.agent.loans.controller;

import com.idfcfirstbank.agent.loans.model.LoanRequest;
import com.idfcfirstbank.agent.loans.model.LoanResponse;
import com.idfcfirstbank.agent.loans.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Loan Agent Service.
 * <p>
 * Provides an AI-powered chat endpoint (for complex queries routed from
 * the orchestrator) and direct Tier 0/Tier 2 endpoints for specific loan operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loan Agent", description = "Loan-related banking operations")
public class LoanController {

    private final LoanService loanService;

    /**
     * Handle loan-related queries via AI agent with function calling.
     * This endpoint is called by the orchestrator for complex loan queries.
     */
    @PostMapping("/process")
    @Operation(summary = "AI-powered loan query", description = "Processes loan queries using LLM with function calling")
    public ResponseEntity<LoanResponse> process(@Valid @RequestBody LoanRequest request) {
        log.info("Loan agent process: customerId={}, intent={}, sessionId={}",
                request.customerId(), request.intent(), request.sessionId());

        LoanResponse response = loanService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Direct loan eligibility check endpoint (Tier 2 - parallel MCP calls, vault policy).
     */
    @PostMapping("/eligibility")
    @Operation(summary = "Loan eligibility check", description = "Tier 2 eligibility check with parallel MCP calls")
    public ResponseEntity<LoanResponse> checkEligibility(@Valid @RequestBody LoanRequest request) {
        log.info("Eligibility check: customerId={}", request.customerId());

        LoanRequest eligibilityRequest = new LoanRequest(
                request.sessionId(),
                request.customerId(),
                request.message(),
                "LOAN_ELIGIBILITY",
                request.confidence(),
                request.parameters()
        );
        LoanResponse response = loanService.processQuery(eligibilityRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Direct EMI query endpoint (Tier 0 - pure Java calculation, no LLM).
     */
    @PostMapping("/emi")
    @Operation(summary = "EMI calculation", description = "Tier 0 EMI calculation without LLM")
    public ResponseEntity<LoanResponse> calculateEmi(@Valid @RequestBody LoanRequest request) {
        log.info("EMI query: customerId={}", request.customerId());

        LoanRequest emiRequest = new LoanRequest(
                request.sessionId(),
                request.customerId(),
                request.message(),
                "LOAN_EMI_QUERY",
                request.confidence(),
                request.parameters()
        );
        LoanResponse response = loanService.processQuery(emiRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Direct prepayment calculation endpoint (Tier 2 - MCP + calculation + vault policy).
     */
    @PostMapping("/prepayment")
    @Operation(summary = "Prepayment calculation", description = "Tier 2 prepayment impact calculation")
    public ResponseEntity<LoanResponse> calculatePrepayment(@Valid @RequestBody LoanRequest request) {
        log.info("Prepayment calculation: customerId={}", request.customerId());

        LoanRequest prepaymentRequest = new LoanRequest(
                request.sessionId(),
                request.customerId(),
                request.message(),
                "LOAN_PREPAYMENT",
                request.confidence(),
                request.parameters()
        );
        LoanResponse response = loanService.processQuery(prepaymentRequest);
        return ResponseEntity.ok(response);
    }
}
