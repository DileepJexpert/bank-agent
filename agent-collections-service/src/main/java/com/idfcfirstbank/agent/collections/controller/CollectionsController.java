package com.idfcfirstbank.agent.collections.controller;

import com.idfcfirstbank.agent.collections.model.CollectionsRequest;
import com.idfcfirstbank.agent.collections.model.CollectionsResponse;
import com.idfcfirstbank.agent.collections.service.CollectionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the Collections Agent Service.
 * <p>
 * Provides endpoints for inbound customer collection calls and direct actions:
 * <ul>
 *   <li>POST /process - Main agent endpoint for inbound customer calls (intent-based routing)</li>
 *   <li>POST /payment-plan - Request restructured EMI payment plan (Tier 2)</li>
 *   <li>POST /settlement - Request settlement offer with discount (Tier 2)</li>
 *   <li>POST /pay-now - Generate payment link for immediate settlement (Tier 0)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
@Tag(name = "Collections Agent", description = "Loan collections, payment recovery, and settlement operations")
public class CollectionsController {

    private final CollectionsService collectionsService;

    /**
     * Main agent endpoint for processing inbound customer collection calls.
     * Routes to the appropriate handler based on the detected intent.
     */
    @PostMapping("/process")
    @Operation(summary = "Process collections query",
            description = "AI-powered collections query processing with intent-based routing")
    public ResponseEntity<CollectionsResponse> processQuery(
            @Valid @RequestBody CollectionsRequest request) {

        log.info("Collections process: customerId={}, intent={}, sessionId={}, loanId={}",
                request.customerId(), request.intent(), request.sessionId(), request.loanId());

        CollectionsResponse response = collectionsService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Request a restructured EMI payment plan for an overdue account (Tier 2 - LLM-assisted).
     * The agent negotiates a feasible repayment schedule with the customer.
     */
    @PostMapping("/payment-plan")
    @Operation(summary = "Request restructured EMI plan",
            description = "Tier 2 - LLM-assisted negotiation of restructured EMI payment plan")
    public ResponseEntity<CollectionsResponse> requestPaymentPlan(
            @Valid @RequestBody CollectionsRequest request) {

        log.info("Payment plan request: customerId={}, loanId={}, overdueAmount={}",
                request.customerId(), request.loanId(), request.overdueAmount());

        CollectionsResponse response = collectionsService.handlePaymentPlan(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Request a settlement offer with applicable discount (Tier 2 - LLM-assisted).
     * Discount percentage is governed by vault policy, not hardcoded.
     */
    @PostMapping("/settlement")
    @Operation(summary = "Request settlement offer",
            description = "Tier 2 - Settlement offer with vault-policy-governed discount")
    public ResponseEntity<CollectionsResponse> requestSettlement(
            @Valid @RequestBody CollectionsRequest request) {

        log.info("Settlement request: customerId={}, loanId={}, overdueAmount={}",
                request.customerId(), request.loanId(), request.overdueAmount());

        CollectionsResponse response = collectionsService.handleSettlementOffer(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate a payment link for immediate settlement (Tier 0 - pure Java, no LLM).
     */
    @PostMapping("/pay-now")
    @Operation(summary = "Generate payment link",
            description = "Tier 0 - Generate immediate payment link without LLM")
    public ResponseEntity<CollectionsResponse> payNow(
            @Valid @RequestBody CollectionsRequest request) {

        log.info("Pay-now request: customerId={}, loanId={}, overdueAmount={}",
                request.customerId(), request.loanId(), request.overdueAmount());

        CollectionsResponse response = collectionsService.handlePayNow(request);
        return ResponseEntity.ok(response);
    }
}
