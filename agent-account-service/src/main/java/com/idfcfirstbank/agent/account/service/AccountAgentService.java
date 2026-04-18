package com.idfcfirstbank.agent.account.service;

import com.idfcfirstbank.agent.account.model.*;
import com.idfcfirstbank.agent.account.tools.AccountTools;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Main service for the Account Agent.
 * Uses {@link LlmRouter} (plain RestClient) instead of Spring AI ChatClient.
 * Switch LLM provider by changing llm.provider in application.yml only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAgentService {

    private static final String SYSTEM_PROMPT = """
            You are the Account Agent for IDFC First Bank. You handle account-related queries
            including balance inquiries, transaction history, cheque book requests, fixed deposits,
            fund transfers, and account details.

            Always verify the customer's identity context before performing sensitive operations.
            Be precise with financial figures and always confirm transaction details before execution.
            Format currency amounts in INR with proper formatting (e.g., Rs 1,25,000.50).
            Be polite and professional. If information is unavailable, say so clearly.
            """;

    private final LlmRouter llmRouter;
    private final VaultClient vaultClient;
    private final AccountTools accountTools;

    /**
     * Process an account query using the AI agent.
     */
    public AccountQueryResponse processQuery(AccountQueryRequest request) {
        PolicyDecision decision = vaultClient.evaluatePolicy(
                "account-agent",
                request.intent() != null ? request.intent() : "account_query",
                "customer:" + request.customerId(),
                Map.of("sessionId", request.sessionId(), "channel", "orchestrator")
        );

        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault denied account query: customerId={}, intent={}, reason={}",
                    request.customerId(), request.intent(), decision.reason());
            return new AccountQueryResponse(
                    request.sessionId(),
                    "I'm unable to process this request at the moment. " + decision.reason(),
                    request.intent(),
                    false
            );
        }

        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Vault escalation: customerId={}, intent={}", request.customerId(), request.intent());
            return new AccountQueryResponse(
                    request.sessionId(),
                    "This request requires additional verification. Connecting you to a specialist.",
                    request.intent(),
                    true
            );
        }

        try {
            String userPrompt = buildUserPrompt(request);
            String response = llmRouter.chat(SYSTEM_PROMPT, userPrompt);
            return new AccountQueryResponse(request.sessionId(), response, request.intent(), false);
        } catch (Exception e) {
            log.error("Error processing account query: customerId={}, intent={}",
                    request.customerId(), request.intent(), e);
            return new AccountQueryResponse(
                    request.sessionId(),
                    "I apologize, but I encountered an issue processing your request. Please try again.",
                    request.intent(),
                    false
            );
        }
    }

    /** Direct balance inquiry — Tier 0, no LLM. */
    public BalanceResponse getBalanceDirect(String customerId, String accountNumber) {
        try {
            String balanceJson = accountTools.getBalanceRaw(customerId, accountNumber);
            return new BalanceResponse(customerId, accountNumber, balanceJson, true);
        } catch (Exception e) {
            log.error("Direct balance inquiry failed: customerId={}", customerId, e);
            return new BalanceResponse(customerId, accountNumber, "Unable to retrieve balance", false);
        }
    }

    /** Get account statement for a date range — Tier 0, no LLM. */
    public AccountQueryResponse getStatement(StatementRequest request) {
        try {
            String history = accountTools.getTransactionHistoryRaw(
                    request.customerId(), request.accountNumber(),
                    request.fromDate(), request.toDate()
            );
            return new AccountQueryResponse(null, history, "MINI_STATEMENT", false);
        } catch (Exception e) {
            log.error("Statement request failed: customerId={}", request.customerId(), e);
            return new AccountQueryResponse(null, "Unable to retrieve statement", "MINI_STATEMENT", false);
        }
    }

    /** Request a new cheque book — Tier 0, no LLM. */
    public AccountQueryResponse requestChequeBook(String customerId, String accountNumber, int leaves) {
        try {
            String result = accountTools.requestChequeBookRaw(customerId, accountNumber, leaves);
            return new AccountQueryResponse(null, result, "CHEQUE_BOOK_REQUEST", false);
        } catch (Exception e) {
            log.error("Cheque book request failed: customerId={}", customerId, e);
            return new AccountQueryResponse(null, "Unable to process cheque book request",
                    "CHEQUE_BOOK_REQUEST", false);
        }
    }

    private String buildUserPrompt(AccountQueryRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Customer ID: ").append(request.customerId()).append("\n");
        prompt.append("Customer message: ").append(request.message()).append("\n");
        if (request.intent() != null) {
            prompt.append("Detected intent: ").append(request.intent()).append("\n");
        }
        if (request.parameters() != null && !request.parameters().isEmpty()) {
            prompt.append("Extracted parameters: ").append(request.parameters()).append("\n");
        }
        prompt.append("\nPlease process this customer's request and provide a clear, friendly response.");
        return prompt.toString();
    }
}
