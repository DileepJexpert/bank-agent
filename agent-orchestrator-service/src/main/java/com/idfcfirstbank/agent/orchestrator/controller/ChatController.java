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

        // Detect intent using tiered approach
        DetectedIntent intent = intentDetectionService.detectIntent(request.message(), sessionId);
        log.info("Detected intent: intent={}, confidence={}, tier={}",
                intent.intent(), intent.confidence(), intent.tier());

        // Route to appropriate agent and get response
        String agentResponse = routingService.routeToAgent(intent, sessionId, request);

        // Record assistant response
        sessionService.addMessage(sessionId, "assistant", agentResponse);

        boolean escalated = intent.tier() >= 3;
        ChatResponse response = new ChatResponse(
                sessionId,
                agentResponse,
                routingService.resolveAgentType(intent.intent()),
                intent.intent(),
                intent.confidence(),
                escalated
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
