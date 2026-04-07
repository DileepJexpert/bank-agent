package com.idfcfirstbank.agent.wealth.controller;

import com.idfcfirstbank.agent.wealth.model.WealthRequest;
import com.idfcfirstbank.agent.wealth.model.WealthResponse;
import com.idfcfirstbank.agent.wealth.service.WealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Wealth Agent Service.
 * <p>
 * Provides both an AI-powered chat endpoint (for complex investment queries routed
 * from the orchestrator) and direct Tier 0/1 endpoints for simpler operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wealth")
@RequiredArgsConstructor
@Tag(name = "Wealth Agent", description = "Wealth management operations including portfolio, SIP, insurance, and investment queries")
public class WealthController {

    private final WealthService wealthService;

    /**
     * Handle wealth-related queries via AI agent with function calling.
     * This endpoint is called by the orchestrator for wealth management queries.
     */
    @PostMapping("/process")
    @Operation(summary = "AI-powered wealth query", description = "Processes wealth queries using LLM with function calling")
    public ResponseEntity<WealthResponse> process(@Valid @RequestBody WealthRequest request) {
        log.info("Wealth agent process: customerId={}, intent={}, sessionId={}",
                request.customerId(), request.intent(), request.sessionId());

        WealthResponse response = wealthService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Portfolio summary endpoint (Tier 1 - no LLM needed).
     * Fetches MF holdings, FDs, and insurance from MCP servers.
     */
    @PostMapping("/portfolio")
    @Operation(summary = "Portfolio summary", description = "Tier 1 portfolio summary without LLM")
    public ResponseEntity<WealthResponse> getPortfolio(@Valid @RequestBody WealthRequest request) {
        log.info("Portfolio summary: customerId={}", request.customerId());

        WealthResponse response = wealthService.handlePortfolioSummary(request);
        return ResponseEntity.ok(response);
    }

    /**
     * SIP management endpoint (Tier 1 - no LLM needed).
     * Create, modify, cancel, or view SIPs.
     */
    @PostMapping("/sip")
    @Operation(summary = "SIP management", description = "Tier 1 SIP create/modify/cancel/view without LLM")
    public ResponseEntity<WealthResponse> manageSip(@Valid @RequestBody WealthRequest request) {
        log.info("SIP management: customerId={}, action={}",
                request.customerId(),
                request.parameters().getOrDefault("action", "view"));

        WealthResponse response = wealthService.handleSipManagement(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Insurance status endpoint (Tier 0 - no LLM needed).
     * Fetches policy status and premium due date.
     */
    @PostMapping("/insurance")
    @Operation(summary = "Insurance status", description = "Tier 0 insurance policy status check")
    public ResponseEntity<WealthResponse> getInsuranceStatus(@Valid @RequestBody WealthRequest request) {
        log.info("Insurance status: customerId={}", request.customerId());

        WealthResponse response = wealthService.handleInsuranceStatus(request);
        return ResponseEntity.ok(response);
    }
}
