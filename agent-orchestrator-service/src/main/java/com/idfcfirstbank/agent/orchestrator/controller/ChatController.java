package com.idfcfirstbank.agent.orchestrator.controller;

import com.idfcfirstbank.agent.orchestrator.model.ChatRequest;
import com.idfcfirstbank.agent.orchestrator.model.ChatResponse;
import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import com.idfcfirstbank.agent.orchestrator.model.SessionInfo;
import com.idfcfirstbank.agent.orchestrator.model.SessionInfo.MessageEntry;
import com.idfcfirstbank.agent.orchestrator.routing.TierRouter;
import com.idfcfirstbank.agent.orchestrator.service.AiIntentDetector;
import com.idfcfirstbank.agent.orchestrator.service.AiIntentDetector.AiIntentResult;
import com.idfcfirstbank.agent.orchestrator.service.AiResponseGenerator;
import com.idfcfirstbank.agent.orchestrator.service.AiToolSelector;
import com.idfcfirstbank.agent.orchestrator.service.AiToolSelector.ToolSelectionResult;
import com.idfcfirstbank.agent.orchestrator.service.IntentDetectionService;
import com.idfcfirstbank.agent.orchestrator.service.LanguageDetectionService;
import com.idfcfirstbank.agent.orchestrator.service.RoutingService;
import com.idfcfirstbank.agent.orchestrator.service.SessionService;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller for synchronous chat interactions and session management.
 * <p>
 * When {@code ai.enabled=true}, uses LLM-based intent detection (Ollama/Llama 3.1)
 * and natural response generation. Falls back to keyword-based detection when AI
 * is disabled or when the LLM call fails.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orchestrator")
@Tag(name = "Orchestrator", description = "Central chat orchestration and session management")
public class ChatController {

    private final IntentDetectionService intentDetectionService;
    private final RoutingService routingService;
    private final SessionService sessionService;
    private final TierRouter tierRouter;
    private final LanguageDetectionService languageDetectionService;
    private final AiIntentDetector aiIntentDetector;
    private final AiResponseGenerator aiResponseGenerator;
    private final AiToolSelector aiToolSelector;
    private final boolean aiEnabled;
    private final LlmRouter llmRouter;

    public ChatController(
            IntentDetectionService intentDetectionService,
            RoutingService routingService,
            SessionService sessionService,
            TierRouter tierRouter,
            LanguageDetectionService languageDetectionService,
            LlmRouter llmRouter,
            @Value("${ai.enabled:false}") boolean aiEnabled,
            // Optional AI beans - only present when ai.enabled=true
            @org.springframework.lang.Nullable AiIntentDetector aiIntentDetector,
            @org.springframework.lang.Nullable AiResponseGenerator aiResponseGenerator,
            @org.springframework.lang.Nullable AiToolSelector aiToolSelector) {
        this.intentDetectionService = intentDetectionService;
        this.routingService = routingService;
        this.sessionService = sessionService;
        this.tierRouter = tierRouter;
        this.languageDetectionService = languageDetectionService;
        this.llmRouter = llmRouter;
        this.aiEnabled = aiEnabled;
        this.aiIntentDetector = aiIntentDetector;
        this.aiResponseGenerator = aiResponseGenerator;
        this.aiToolSelector = aiToolSelector;

        if (aiEnabled && aiIntentDetector != null) {
            log.info("AI-powered intent detection ENABLED with provider: {}", llmRouter.getActiveProvider());
        } else {
            log.info("AI-powered intent detection DISABLED, using keyword fallback");
        }
    }

    /**
     * Synchronous chat endpoint. Detects intent, routes to the appropriate domain agent,
     * and returns the response.
     * <p>
     * When AI is enabled: uses LLM for intent detection and response generation.
     * When AI is disabled or fails: falls back to keyword-based detection.
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

        // Detect language
        String detectedLanguage = languageDetectionService.detectLanguage(request.message());

        // Try AI intent detection first, fall back to keyword matching
        List<DetectedIntent> intents;
        String usedModel;
        double intentConfidence;

        if (aiEnabled && aiIntentDetector != null) {
            AiIntentResult aiResult = aiIntentDetector.detect(request.message());
            if (aiResult != null && !aiResult.intents().isEmpty()) {
                intents = aiResult.intents();
                usedModel = llmRouter.getActiveProvider();
                intentConfidence = aiResult.confidence();
                detectedLanguage = aiResult.language();
                log.info("AI intent detection succeeded: model={}, intents={}, confidence={}, language={}",
                        usedModel, intents.size(), intentConfidence, detectedLanguage);
            } else {
                log.warn("AI intent detection returned null/empty, falling back to keyword matching");
                intents = intentDetectionService.detectIntents(request.message(), sessionId);
                usedModel = "keyword-fallback";
                intentConfidence = intents.isEmpty() ? 0.0 : intents.getFirst().confidence();
            }
        } else {
            intents = intentDetectionService.detectIntents(request.message(), sessionId);
            usedModel = "keyword-fallback";
            intentConfidence = intents.isEmpty() ? 0.0 : intents.getFirst().confidence();
        }

        DetectedIntent primaryIntent = intents.isEmpty()
                ? new DetectedIntent("UNKNOWN", 0.0, 2, Map.of())
                : intents.getFirst();
        log.info("Detected {} intent(s): primary={}, confidence={}, tier={}, model={}",
                intents.size(), primaryIntent.intent(), primaryIntent.confidence(),
                primaryIntent.tier(), usedModel);

        // Check if clarification is needed
        boolean clarificationNeeded = intents.stream()
                .anyMatch(i -> "CLARIFICATION_NEEDED".equals(i.intent()));

        // Route to appropriate agent(s) and get response
        String agentResponse;
        List<String> toolsCalled;

        // Tier 2+ with AI: use AiToolSelector (LLM decides which tools to call)
        boolean usedToolSelector = false;
        if (aiEnabled && aiToolSelector != null && !clarificationNeeded && primaryIntent.tier() >= 2) {
            log.info("Tier 2 query — using AI Tool Selector (LLM function calling)");
            ToolSelectionResult toolResult = aiToolSelector.process(
                    request.customerId(), request.message(), detectedLanguage);
            if (toolResult.success() && toolResult.response() != null) {
                agentResponse = toolResult.response();
                toolsCalled = new ArrayList<>(toolResult.toolsCalled());
                toolsCalled.add("aiToolSelector");
                usedToolSelector = true;
                log.info("AI Tool Selector succeeded: {} tools called", toolResult.toolsCalled().size());
            } else {
                log.warn("AI Tool Selector failed, falling back to rule-based routing");
                agentResponse = routingService.routeToAgents(intents, sessionId, request);
                toolsCalled = routingService.getToolsCalled();
            }
        } else {
            // Tier 0/1 or AI disabled: use rule-based routing
            agentResponse = routingService.routeToAgents(intents, sessionId, request);
            toolsCalled = routingService.getToolsCalled();
        }

        // For Tier 0/1 with AI enabled, generate natural language response
        if (aiEnabled && aiResponseGenerator != null && !clarificationNeeded && !usedToolSelector) {
            String naturalResponse = aiResponseGenerator.generate(
                    request.message(), detectedLanguage, agentResponse);
            if (naturalResponse != null && !naturalResponse.isBlank()) {
                agentResponse = naturalResponse;
                toolsCalled.add("aiResponseGenerator");
            }
        }

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
                primaryIntent.tier(),
                usedModel,
                intentConfidence,
                detectedLanguage,
                toolsCalled
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
