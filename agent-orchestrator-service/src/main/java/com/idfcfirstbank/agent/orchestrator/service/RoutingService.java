package com.idfcfirstbank.agent.orchestrator.service;

import com.idfcfirstbank.agent.orchestrator.model.ChatRequest;
import com.idfcfirstbank.agent.orchestrator.model.DetectedIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Routes detected intents to the appropriate domain agent service.
 * <p>
 * Supports multi-intent routing: when multiple intents are detected, each intent
 * is routed sequentially and responses are aggregated.
 * <p>
 * When confidence is below threshold (CLARIFICATION_NEEDED), returns a clarifying
 * question instead of routing to a downstream service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final RestTemplate restTemplate;

    @Value("${agent.routing.account-service-url:http://agent-account-service:8085}")
    private String accountServiceUrl;

    @Value("${agent.routing.card-service-url:http://agent-card-service:8088}")
    private String cardServiceUrl;

    @Value("${agent.routing.loans-service-url:http://agent-loans-service:8090}")
    private String loansServiceUrl;

    /** Intents handled by the Account Agent. */
    private static final Set<String> ACCOUNT_INTENTS = Set.of(
            "BALANCE_INQUIRY", "MINI_STATEMENT", "FUND_TRANSFER",
            "CHEQUE_STATUS", "CHEQUE_BOOK_REQUEST", "FD_CREATION",
            "ACCOUNT_DETAILS", "INTEREST_CERTIFICATE"
    );

    /** Intents handled by the Card Agent. */
    private static final Set<String> CARD_INTENTS = Set.of(
            "CARD_BLOCK", "CARD_ACTIVATE", "CARD_LIMIT", "CARD_STATEMENT",
            "CARD_DISPUTE", "REWARD_POINTS", "DISPUTE_RAISE"
    );

    /** Intents handled by the Loans Agent. */
    private static final Set<String> LOAN_INTENTS = Set.of(
            "LOAN_INQUIRY", "LOAN_APPLICATION", "LOAN_REPAYMENT", "LOAN_FORECLOSURE",
            "LOAN_ELIGIBILITY", "LOAN_EMI_QUERY", "LOAN_PREPAYMENT"
    );

    /**
     * Route multiple detected intents sequentially and aggregate responses.
     *
     * @param intents   the list of detected intents
     * @param sessionId the conversation session identifier
     * @param request   the original chat request
     * @return aggregated agent response text
     */
    public String routeToAgents(List<DetectedIntent> intents, String sessionId, ChatRequest request) {
        if (intents == null || intents.isEmpty()) {
            return "I'm not sure I understand your request. Could you please rephrase or provide more details?";
        }

        // Check for clarification needed
        boolean anyClarificationNeeded = intents.stream()
                .anyMatch(i -> "CLARIFICATION_NEEDED".equals(i.intent()));
        if (anyClarificationNeeded) {
            return handleClarificationNeeded(request.message());
        }

        // Single intent: route directly
        if (intents.size() == 1) {
            return routeToAgent(intents.getFirst(), sessionId, request);
        }

        // Multi-intent: route sequentially and aggregate
        log.info("Multi-intent routing: {} intents for sessionId={}", intents.size(), sessionId);
        List<String> responses = new ArrayList<>();
        for (DetectedIntent intent : intents) {
            String response = routeToAgent(intent, sessionId, request);
            responses.add(response);
        }

        return responses.stream()
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * Route a single detected intent to the appropriate domain agent and return the response.
     *
     * @param intent    the detected intent
     * @param sessionId the conversation session identifier
     * @param request   the original chat request
     * @return the agent's response text
     */
    public String routeToAgent(DetectedIntent intent, String sessionId, ChatRequest request) {
        String intentName = intent.intent().toUpperCase();
        log.info("Routing intent={} for sessionId={}", intentName, sessionId);

        if ("CLARIFICATION_NEEDED".equals(intentName)) {
            return handleClarificationNeeded(request.message());
        }

        try {
            if (ACCOUNT_INTENTS.contains(intentName)) {
                return callAccountAgent(intent, sessionId, request);
            } else if (CARD_INTENTS.contains(intentName)) {
                return callAgentService(cardServiceUrl + "/api/v1/cards/chat", intent, sessionId, request);
            } else if (LOAN_INTENTS.contains(intentName)) {
                return callAgentService(loansServiceUrl + "/api/v1/loans/chat", intent, sessionId, request);
            } else if ("GENERAL_INQUIRY".equals(intentName)) {
                return handleGeneralInquiry(request.message());
            } else if ("COMPLAINT".equals(intentName)) {
                return "I understand you have a concern. Let me connect you with our customer support team "
                        + "who can assist you further. Your complaint reference will be shared shortly.";
            } else {
                return "I'm not sure I understand your request. Could you please rephrase or provide more details?";
            }
        } catch (Exception e) {
            log.error("Error routing to agent for intent={}: {}", intentName, e.getMessage(), e);
            return "I apologize, but I'm experiencing a temporary issue. Please try again in a moment.";
        }
    }

    /**
     * Resolve the agent type name for the given intent.
     */
    public String resolveAgentType(String intent) {
        String intentUpper = intent.toUpperCase();
        if (ACCOUNT_INTENTS.contains(intentUpper)) return "ACCOUNT";
        if (CARD_INTENTS.contains(intentUpper)) return "CARD";
        if (LOAN_INTENTS.contains(intentUpper)) return "LOAN";
        if ("GENERAL_INQUIRY".equals(intentUpper)) return "ORCHESTRATOR";
        if ("COMPLAINT".equals(intentUpper)) return "ORCHESTRATOR";
        if ("CLARIFICATION_NEEDED".equals(intentUpper)) return "ORCHESTRATOR";
        return "ORCHESTRATOR";
    }

    /**
     * Resolve agent types for multiple intents.
     */
    public String resolveAgentTypes(List<DetectedIntent> intents) {
        if (intents == null || intents.isEmpty()) {
            return "ORCHESTRATOR";
        }
        if (intents.size() == 1) {
            return resolveAgentType(intents.getFirst().intent());
        }
        return intents.stream()
                .map(i -> resolveAgentType(i.intent()))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String handleClarificationNeeded(String originalMessage) {
        return "I'd like to help you, but I'm not entirely sure what you need. "
                + "Could you please provide more details about your request? "
                + "For example, I can help with account balances, fund transfers, card services, "
                + "loans, fixed deposits, and more.";
    }

    private String callAccountAgent(DetectedIntent intent, String sessionId, ChatRequest request) {
        String endpoint = accountServiceUrl + "/api/v1/accounts/chat";
        return callAgentService(endpoint, intent, sessionId, request);
    }

    @SuppressWarnings("unchecked")
    private String callAgentService(String url, DetectedIntent intent, String sessionId, ChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "sessionId", sessionId,
                "customerId", request.customerId(),
                "message", request.message(),
                "intent", intent.intent(),
                "confidence", intent.confidence(),
                "parameters", intent.parameters()
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            if (response != null && response.containsKey("message")) {
                return response.get("message").toString();
            }
            return "Your request has been processed.";
        } catch (RestClientException e) {
            log.error("Failed to call agent service at {}: {}", url, e.getMessage());
            return "I apologize, but the service is temporarily unavailable. Please try again shortly.";
        }
    }

    private String handleGeneralInquiry(String message) {
        return "Thank you for your inquiry. I'm here to help you with your banking needs. "
                + "I can assist with account balances, fund transfers, fixed deposits, cheque books, "
                + "card services, and loan information. How may I help you today?";
    }
}
