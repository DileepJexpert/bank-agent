package com.idfcfirstbank.agent.orchestrator.controller;

import com.idfcfirstbank.agent.orchestrator.model.ChatRequest;
import com.idfcfirstbank.agent.orchestrator.model.ChatResponse;
import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import com.idfcfirstbank.agent.orchestrator.model.SessionInfo;
import com.idfcfirstbank.agent.orchestrator.model.SessionInfo.MessageEntry;
import com.idfcfirstbank.agent.orchestrator.routing.TierRouter;
import com.idfcfirstbank.agent.orchestrator.service.IntentDetectionService;
import com.idfcfirstbank.agent.orchestrator.service.RoutingService;
import com.idfcfirstbank.agent.orchestrator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for synchronous chat interactions and session management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orchestrator")
@RequiredArgsConstructor
@Tag(name = "Orchestrator", description = "Central chat orchestration and session management")
public class ChatController {

    private final IntentDetectionService intentDetectionService;
    private final RoutingService routingService;
    private final SessionService sessionService;
    private final TierRouter tierRouter;

    /**
     * Synchronous chat endpoint. Detects intent, routes to the appropriate domain agent,
     * and returns the response.
     */
    @PostMapping("/chat")
    @Operation(summary = "Send a chat message", description = "Detects intent, routes to agent, returns response")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: customerId={}, sessionId={}, message length={}",
                request.customerId(), request.sessionId(), request.message().length());

        // Ensure a session exists
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            SessionInfo newSession = sessionService.createSession(
                    request.customerId(), request.channel(), request.language());
            sessionId = newSession.sessionId();
        }

        // Record customer message
        sessionService.addMessage(sessionId, "customer", request.message());

        // Detect intents using tiered approach (supports multi-intent)
        List<DetectedIntent> intents = intentDetectionService.detectIntents(request.message(), sessionId);
        DetectedIntent primaryIntent = intents.isEmpty()
                ? new DetectedIntent("UNKNOWN", 0.0, 2, Map.of())
                : intents.getFirst();
        log.info("Detected {} intent(s): primary={}, confidence={}, tier={}",
                intents.size(), primaryIntent.intent(), primaryIntent.confidence(), primaryIntent.tier());

        // Check if clarification is needed
        boolean clarificationNeeded = intents.stream()
                .anyMatch(i -> "CLARIFICATION_NEEDED".equals(i.intent()));

        // Route to appropriate agent(s) and get aggregated response
        String agentResponse = routingService.routeToAgents(intents, sessionId, request);

        // Record assistant response
        sessionService.addMessage(sessionId, "assistant", agentResponse);

        boolean escalated = primaryIntent.tier() >= 3;
        ChatResponse response = new ChatResponse(
                sessionId,
                agentResponse,
                routingService.resolveAgentTypes(intents),
                primaryIntent.intent(),
                primaryIntent.confidence(),
                escalated,
                intents,
                clarificationNeeded,
                primaryIntent.tier()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Start a new conversation session.
     */
    @PostMapping("/session/start")
    @Operation(summary = "Start a new session")
    public ResponseEntity<SessionInfo> startSession(@RequestBody Map<String, String> body) {
        String customerId = body.getOrDefault("customerId", "anonymous");
        String channel = body.getOrDefault("channel", "web");
        String language = body.getOrDefault("language", "en");

        SessionInfo session = sessionService.createSession(customerId, channel, language);
        return ResponseEntity.ok(session);
    }

    /**
     * End an existing conversation session.
     */
    @PostMapping("/session/{sessionId}/end")
    @Operation(summary = "End a session")
    public ResponseEntity<Map<String, String>> endSession(@PathVariable String sessionId) {
        sessionService.endSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "ended", "sessionId", sessionId));
    }

    /**
     * Retrieve conversation history for a session.
     */
    @GetMapping("/session/{sessionId}/history")
    @Operation(summary = "Get conversation history")
    public ResponseEntity<List<MessageEntry>> getHistory(@PathVariable String sessionId) {
        List<MessageEntry> history = sessionService.getHistory(sessionId);
        return ResponseEntity.ok(history);
    }
}
