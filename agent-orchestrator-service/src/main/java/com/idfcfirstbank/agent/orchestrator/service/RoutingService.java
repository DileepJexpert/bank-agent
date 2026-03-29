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

import java.util.Map;
import java.util.Set;

/**
 * Routes detected intents to the appropriate domain agent service.
 * <p>
 * Uses RestTemplate to make synchronous calls to downstream agent services.
 * Each intent maps to a specific agent type and endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final RestTemplate restTemplate;

    @Value("${agent.routing.account-service-url:http://agent-account-service:8085}")
    private String accountServiceUrl;

    @Value("${agent.routing.card-service-url:http://agent-card-service:8087}")
    private String cardServiceUrl;

    @Value("${agent.routing.loan-service-url:http://agent-loan-service:8088}")
    private String loanServiceUrl;

    /** Intents handled by the Account Agent. */
    private static final Set<String> ACCOUNT_INTENTS = Set.of(
            "BALANCE_INQUIRY", "MINI_STATEMENT", "FUND_TRANSFER",
            "CHEQUE_STATUS", "CHEQUE_BOOK_REQUEST", "FD_CREATION",
            "ACCOUNT_DETAILS", "INTEREST_CERTIFICATE"
    );

    /** Intents handled by the Card Agent. */
    private static final Set<String> CARD_INTENTS = Set.of(
            "CARD_BLOCK", "CARD_LIMIT", "CARD_STATEMENT", "CARD_DISPUTE"
    );

    /** Intents handled by the Loan Agent. */
    private static final Set<String> LOAN_INTENTS = Set.of(
            "LOAN_INQUIRY", "LOAN_APPLICATION", "LOAN_REPAYMENT", "LOAN_FORECLOSURE"
    );

    /**
     * Route the detected intent to the appropriate domain agent and return the response.
     *
     * @param intent    the detected intent
     * @param sessionId the conversation session identifier
     * @param request   the original chat request
     * @return the agent's response text
     */
    public String routeToAgent(DetectedIntent intent, String sessionId, ChatRequest request) {
        String intentName = intent.intent().toUpperCase();
        log.info("Routing intent={} for sessionId={}", intentName, sessionId);

        try {
            if (ACCOUNT_INTENTS.contains(intentName)) {
                return callAccountAgent(intent, sessionId, request);
            } else if (CARD_INTENTS.contains(intentName)) {
                return callAgentService(cardServiceUrl + "/api/v1/cards/chat", intent, sessionId, request);
            } else if (LOAN_INTENTS.contains(intentName)) {
                return callAgentService(loanServiceUrl + "/api/v1/loans/chat", intent, sessionId, request);
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
        return "ORCHESTRATOR";
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
