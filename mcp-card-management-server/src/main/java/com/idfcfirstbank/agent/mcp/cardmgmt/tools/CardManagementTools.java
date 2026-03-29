package com.idfcfirstbank.agent.mcp.cardmgmt.tools;

import com.idfcfirstbank.agent.mcp.cardmgmt.client.CardSystemClient;
import com.idfcfirstbank.agent.mcp.cardmgmt.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP tool definitions for card management, exposed as REST endpoints.
 * <p>
 * Each endpoint acts as an MCP-compatible tool that can be invoked by upstream
 * agent services (e.g. agent-card-service). The tools delegate to
 * {@link CardSystemClient} for actual card operations with resilience patterns applied.
 * <p>
 * CRITICAL SECURITY: Card numbers are NEVER logged or returned in full.
 * Only the last 4 digits (cardLast4) are accepted and used for identification.
 * <p>
 * Tools exposed:
 * <ul>
 *   <li>activateCard - Activate a new or replacement card</li>
 *   <li>blockCard - Block a card immediately</li>
 *   <li>changeLimit - Change card transaction limits</li>
 *   <li>getRewardPoints - Retrieve reward points balance</li>
 *   <li>redeemPoints - Redeem reward points</li>
 *   <li>raiseDispute - Raise a transaction dispute</li>
 *   <li>convertToEMI - Convert a transaction to EMI</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/tools")
@RequiredArgsConstructor
@Tag(name = "Card Management Tools", description = "MCP-compatible card management tool endpoints")
public class CardManagementTools {

    private final CardSystemClient cardSystemClient;

    /**
     * Tool: activateCard - Activate a new or replacement card.
     */
    @PostMapping("/activateCard")
    @Operation(summary = "Activate card",
            description = "MCP tool: Activates a new or replacement card for the customer")
    public ResponseEntity<CardActivation> activateCard(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String cardLast4 = request.getOrDefault("cardLast4", "");

        log.info("MCP Tool: activateCard - customerId={}, cardLast4=****{}", customerId, cardLast4);

        CardActivation result = cardSystemClient.activateCard(customerId, cardLast4);
        return ResponseEntity.ok(result);
    }

    /**
     * Tool: blockCard - Block a card immediately.
     */
    @PostMapping("/blockCard")
    @Operation(summary = "Block card",
            description = "MCP tool: Blocks a card immediately for the customer")
    public ResponseEntity<CardBlock> blockCard(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String cardLast4 = request.getOrDefault("cardLast4", "");
        String reason = request.getOrDefault("reason", "CUSTOMER_REQUEST");

        log.info("MCP Tool: blockCard - customerId={}, cardLast4=****{}, reason={}",
                customerId, cardLast4, reason);

        CardBlock result = cardSystemClient.blockCard(customerId, cardLast4, reason);
        return ResponseEntity.ok(result);
    }

    /**
     * Tool: changeLimit - Change card transaction limits.
     */
    @PostMapping("/changeLimit")
    @Operation(summary = "Change card limit",
            description = "MCP tool: Changes the transaction limit for a card")
    public ResponseEntity<Map<String, Object>> changeLimit(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String cardLast4 = request.getOrDefault("cardLast4", "");
        String newLimit = request.getOrDefault("newLimit", "0");
        String limitType = request.getOrDefault("limitType", "DAILY");

        log.info("MCP Tool: changeLimit - customerId={}, cardLast4=****{}, newLimit={}, type={}",
                customerId, cardLast4, newLimit, limitType);

        Map<String, Object> result = cardSystemClient.changeLimit(customerId, cardLast4, newLimit, limitType);
        return ResponseEntity.ok(result);
    }

    /**
     * Tool: getRewardPoints - Retrieve reward points balance and history.
     */
    @GetMapping("/getRewardPoints")
    @Operation(summary = "Get reward points",
            description = "MCP tool: Retrieves reward points balance and recent earnings")
    public ResponseEntity<RewardPoints> getRewardPoints(@RequestParam String customerId) {
        log.info("MCP Tool: getRewardPoints - customerId={}", customerId);

        RewardPoints result = cardSystemClient.getRewardPoints(customerId);
        return ResponseEntity.ok(result);
    }

    /**
     * Tool: redeemPoints - Redeem reward points.
     */
    @PostMapping("/redeemPoints")
    @Operation(summary = "Redeem reward points",
            description = "MCP tool: Redeems reward points for the customer")
    public ResponseEntity<Map<String, Object>> redeemPoints(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String points = request.getOrDefault("points", "0");
        String redemptionType = request.getOrDefault("redemptionType", "STATEMENT_CREDIT");

        log.info("MCP Tool: redeemPoints - customerId={}, points={}, type={}",
                customerId, points, redemptionType);

        Map<String, Object> result = cardSystemClient.redeemPoints(customerId, points, redemptionType);
        return ResponseEntity.ok(result);
    }

    /**
     * Tool: raiseDispute - Raise a transaction dispute.
     */
    @PostMapping("/raiseDispute")
    @Operation(summary = "Raise dispute",
            description = "MCP tool: Raises a dispute for a card transaction")
    public ResponseEntity<DisputeResult> raiseDispute(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String transactionId = request.getOrDefault("transactionId", "");
        String reason = request.getOrDefault("reason", "");
        String amount = request.getOrDefault("amount", "0");

        log.info("MCP Tool: raiseDispute - customerId={}, transactionId={}, amount={}",
                customerId, transactionId, amount);

        DisputeResult result = cardSystemClient.raiseDispute(customerId, transactionId, reason, amount);
        return ResponseEntity.ok(result);
    }

    /**
     * Tool: convertToEMI - Convert a transaction to EMI.
     */
    @PostMapping("/convertToEMI")
    @Operation(summary = "Convert to EMI",
            description = "MCP tool: Converts a card transaction into EMI instalments")
    public ResponseEntity<EMIConversion> convertToEMI(@RequestBody Map<String, String> request) {
        String customerId = request.getOrDefault("customerId", "");
        String transactionId = request.getOrDefault("transactionId", "");
        String tenure = request.getOrDefault("tenure", "12");

        log.info("MCP Tool: convertToEMI - customerId={}, transactionId={}, tenure={}",
                customerId, transactionId, tenure);

        EMIConversion result = cardSystemClient.convertToEMI(customerId, transactionId, tenure);
        return ResponseEntity.ok(result);
    }
}
