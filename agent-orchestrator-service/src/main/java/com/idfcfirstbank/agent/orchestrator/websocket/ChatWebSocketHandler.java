package com.idfcfirstbank.agent.orchestrator.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.orchestrator.model.ChatRequest;
import com.idfcfirstbank.agent.orchestrator.model.ChatResponse;
import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import com.idfcfirstbank.agent.orchestrator.model.SessionInfo;
import com.idfcfirstbank.agent.orchestrator.routing.TierRouter;
import com.idfcfirstbank.agent.orchestrator.service.IntentDetectionService;
import com.idfcfirstbank.agent.orchestrator.service.RoutingService;
import com.idfcfirstbank.agent.orchestrator.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time bidirectional chat.
 * <p>
 * Each WebSocket connection is associated with a conversation session.
 * Messages are routed through the same intent detection and routing pipeline
 * as the REST endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final IntentDetectionService intentDetectionService;
    private final RoutingService routingService;
    private final SessionService sessionService;

    /** Maps WebSocket session ID to conversation session ID. */
    private final Map<String, String> wsToConversationSession = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: wsSessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("WebSocket message received: wsSessionId={}, length={}", wsSession.getId(), payload.length());

        try {
            ChatRequest request = objectMapper.readValue(payload, ChatRequest.class);

            // Resolve or create conversation session
            String sessionId = resolveSessionId(wsSession, request);

            // Record customer message
            sessionService.addMessage(sessionId, "customer", request.message());

            // Detect intent
            DetectedIntent intent = intentDetectionService.detectIntent(request.message(), sessionId);

            // Route to agent
            String agentResponse = routingService.routeToAgent(intent, sessionId, request);

            // Record response
            sessionService.addMessage(sessionId, "assistant", agentResponse);

            // Build and send response
            ChatResponse response = new ChatResponse(
                    sessionId,
                    agentResponse,
                    routingService.resolveAgentType(intent.intent()),
                    intent.intent(),
                    intent.confidence(),
                    intent.tier() >= 3
            );

            sendMessage(wsSession, response);

        } catch (Exception e) {
            log.error("Error processing WebSocket message: wsSessionId={}", wsSession.getId(), e);
            sendError(wsSession, "An error occurred processing your message. Please try again.");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String conversationSessionId = wsToConversationSession.remove(session.getId());
        log.info("WebSocket connection closed: wsSessionId={}, conversationSessionId={}, status={}",
                session.getId(), conversationSessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: wsSessionId={}", session.getId(), exception);
        wsToConversationSession.remove(session.getId());
    }

    private String resolveSessionId(WebSocketSession wsSession, ChatRequest request) {
        String existingSessionId = wsToConversationSession.get(wsSession.getId());
        if (existingSessionId != null) {
            return existingSessionId;
        }

        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            wsToConversationSession.put(wsSession.getId(), request.sessionId());
            return request.sessionId();
        }

        SessionInfo newSession = sessionService.createSession(
                request.customerId(), request.channel(), request.language());
        wsToConversationSession.put(wsSession.getId(), newSession.sessionId());
        return newSession.sessionId();
    }

    private void sendMessage(WebSocketSession session, ChatResponse response) throws IOException {
        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            Map<String, String> error = Map.of("error", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            log.error("Failed to send error message via WebSocket", e);
        }
    }
}
