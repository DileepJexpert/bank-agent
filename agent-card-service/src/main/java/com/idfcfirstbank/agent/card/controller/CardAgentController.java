package com.idfcfirstbank.agent.card.controller;

import com.idfcfirstbank.agent.card.model.CardAgentRequest;
import com.idfcfirstbank.agent.card.model.CardAgentResponse;
import com.idfcfirstbank.agent.card.model.DisputeRequest;
import com.idfcfirstbank.agent.card.model.RewardPointsResponse;
import com.idfcfirstbank.agent.card.service.CardAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Card Agent Service.
 * <p>
 * Provides an AI-powered chat endpoint for complex card queries routed from
 * the orchestrator, plus direct endpoints for tiered operations:
 * <ul>
 *   <li>Tier 0: Simple lookups (reward points) - no LLM</li>
 *   <li>Tier 1: Urgent actions (block, activate) - immediate MCP call</li>
 *   <li>Tier 2: Conversational flows (dispute, EMI) - LLM-assisted</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Card Agent", description = "Card-related banking operations with PCI-DSS compliance")
public class CardAgentController {

    private final CardAgentService cardAgentService;

    /**
     * Main agent endpoint for processing card-related queries via AI with function calling.
     * Called by the orchestrator for all card intents.
     */
    @PostMapping("/process")
    @Operation(summary = "Process card query",
            description = "AI-powered card query processing with tiered execution")
    public ResponseEntity<CardAgentResponse> processCardQuery(
            @Valid @RequestBody CardAgentRequest request) {

        log.info("Card agent process: customerId={}, intent={}, sessionId={}",
                request.customerId(), request.intent(), request.sessionId());

        CardAgentResponse response = cardAgentService.processCardQuery(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Direct card block endpoint (Tier 1 - urgent, no LLM needed).
     * High-priority operation that immediately calls the MCP card management server.
     */
    @PostMapping("/block")
    @Operation(summary = "Block card immediately",
            description = "Tier 1 urgent card block - bypasses LLM for speed")
    public ResponseEntity<CardAgentResponse> blockCard(@RequestBody Map<String, String> body) {
        String customerId = body.get("customerId");
        String cardLast4 = body.getOrDefault("cardLast4", "");
        String reason = body.getOrDefault("reason", "CUSTOMER_REQUEST");

        log.info("Direct card block: customerId={}, cardLast4=****{}", customerId, cardLast4);

        CardAgentResponse response = cardAgentService.blockCardDirect(customerId, cardLast4, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * Reward points inquiry endpoint (Tier 0 - no LLM needed).
     */
    @GetMapping("/rewards/{customerId}")
    @Operation(summary = "Get reward points",
            description = "Tier 0 reward points inquiry - direct MCP call without LLM")
    public ResponseEntity<RewardPointsResponse> getRewardPoints(
            @PathVariable String customerId) {

        log.info("Reward points inquiry: customerId={}", customerId);

        RewardPointsResponse response = cardAgentService.getRewardPointsDirect(customerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Raise a card transaction dispute (Tier 2 - LLM-assisted for detail collection).
     */
    @PostMapping("/dispute")
    @Operation(summary = "Raise card dispute",
            description = "Tier 2 dispute raising - LLM-assisted detail collection and MCP call")
    public ResponseEntity<CardAgentResponse> raiseDispute(
            @Valid @RequestBody DisputeRequest request) {

        log.info("Dispute request: customerId={}, transactionId={}, amount={}",
                request.customerId(), request.transactionId(), request.amount());

        CardAgentResponse response = cardAgentService.raiseDisputeDirect(request);
        return ResponseEntity.ok(response);
    }
}
