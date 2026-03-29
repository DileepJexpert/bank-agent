package com.idfcfirstbank.agent.account.service;

import com.idfcfirstbank.agent.account.model.*;
import com.idfcfirstbank.agent.account.tools.AccountTools;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Main service for the Account Agent. Orchestrates AI-powered query processing
 * with Spring AI function calling and policy enforcement via Vault.
 * <p>
 * For each query, the service:
 * <ol>
 *   <li>Checks vault policy to verify the action is permitted</li>
 *   <li>Uses ChatClient with registered tool functions to process the query</li>
 *   <li>Returns a structured response</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAgentService {

    private final ChatClient chatClient;
    private final VaultClient vaultClient;
    private final AccountTools accountTools;

    /**
     * Process an account query using the AI agent with function calling.
     */
    public AccountQueryResponse processQuery(AccountQueryRequest request) {
        // Vault policy check
        PolicyDecision decision = vaultClient.evaluatePolicy(
                "account-agent",
                request.intent() != null ? request.intent() : "account_query",
                "customer:" + request.customerId(),
                Map.of("sessionId", request.sessionId(), "channel", "orchestrator")
        );

        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault policy denied account query: customerId={}, intent={}, reason={}",
                    request.customerId(), request.intent(), decision.reason());
            return new AccountQueryResponse(
                    request.sessionId(),
                    "I'm unable to process this request at the moment. " + decision.reason(),
                    request.intent(),
                    false
            );
        }

        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            log.info("Vault policy requires escalation: customerId={}, intent={}",
                    request.customerId(), request.intent());
            return new AccountQueryResponse(
                    request.sessionId(),
                    "This request requires additional verification. Connecting you to a specialist.",
                    request.intent(),
                    true
            );
        }

        // Use ChatClient with function calling for AI-powered processing
        try {
            String userPrompt = buildUserPrompt(request);

            String response = chatClient.prompt()
                    .user(userPrompt)
                    .functions(
                            "getBalance",
                            "getTransactionHistory",
                            "getAccountDetails",
                            "requestChequeBook",
                            "getMiniStatement"
                    )
                    .call()
                    .content();

            return new AccountQueryResponse(
                    request.sessionId(),
                    response,
                    request.intent(),
                    false
            );

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

    /**
     * Direct balance inquiry without LLM (Tier 0).
     */
    public BalanceResponse getBalanceDirect(String customerId, String accountNumber) {
        try {
            String balanceJson = accountTools.getBalanceRaw(customerId, accountNumber);
            // Parse and return structured response
            return new BalanceResponse(
                    customerId,
                    accountNumber,
                    balanceJson,
                    true
            );
        } catch (Exception e) {
            log.error("Direct balance inquiry failed: customerId={}", customerId, e);
            return new BalanceResponse(customerId, accountNumber, "Unable to retrieve balance", false);
        }
    }

    /**
     * Get account statement for a date range.
     */
    public AccountQueryResponse getStatement(StatementRequest request) {
        try {
            String history = accountTools.getTransactionHistoryRaw(
                    request.customerId(), request.accountNumber(),
                    request.fromDate(), request.toDate()
            );
            return new AccountQueryResponse(
                    null,
                    history,
                    "MINI_STATEMENT",
                    false
            );
        } catch (Exception e) {
            log.error("Statement request failed: customerId={}", request.customerId(), e);
            return new AccountQueryResponse(null, "Unable to retrieve statement", "MINI_STATEMENT", false);
        }
    }

    /**
     * Request a new cheque book.
     */
    public AccountQueryResponse requestChequeBook(String customerId, String accountNumber, int leaves) {
        try {
            String result = accountTools.requestChequeBookRaw(customerId, accountNumber, leaves);
            return new AccountQueryResponse(null, result, "CHEQUE_BOOK_REQUEST", false);
        } catch (Exception e) {
            log.error("Cheque book request failed: customerId={}", customerId, e);
            return new AccountQueryResponse(null, "Unable to process cheque book request", "CHEQUE_BOOK_REQUEST", false);
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

        prompt.append("\nPlease process this customer's request using the available tools. ");
        prompt.append("Provide a clear, friendly response with the relevant information.");

        return prompt.toString();
    }
}
