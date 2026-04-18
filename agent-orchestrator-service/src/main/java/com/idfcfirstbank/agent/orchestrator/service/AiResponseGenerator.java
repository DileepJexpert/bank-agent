package com.idfcfirstbank.agent.orchestrator.service;

import com.idfcfirstbank.agent.common.llm.LlmRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * LLM-based response generator that produces natural, friendly banking responses.
 * <p>
 * Activated when {@code ai.enabled=true}. Takes the raw MCP/agent response data
 * and generates a conversational reply in the customer's detected language.
 * <p>
 * Security: never reveals full account numbers or card numbers.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class AiResponseGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a helpful IDFC First Bank assistant.
            Generate a natural, friendly response using the provided data.
            Reply in the same language the customer used.
            Be concise - max 2-3 sentences.
            Never reveal full account numbers or card numbers.
            Always mask sensitive data (e.g., show only last 4 digits).
            If the data indicates an error or unavailability, apologize politely.
            """;

    private final LlmRouter llmRouter;

    public AiResponseGenerator(LlmRouter llmRouter) {
        this.llmRouter = llmRouter;
    }

    /**
     * Generate a natural language response from agent/MCP data.
     *
     * @param customerMessage the original customer message
     * @param language        detected language code (en, hi, hi-en, etc.)
     * @param agentResponse   raw response text from the domain agent
     * @return natural language response in the customer's language
     */
    public String generate(String customerMessage, String language, String agentResponse) {
        log.debug("Generating AI response: language={}, agentResponse length={}",
                language, agentResponse != null ? agentResponse.length() : 0);

        try {
            String prompt = String.format("""
                    Customer said: %s
                    Language: %s
                    Data from bank systems: %s
                    Generate a natural, friendly response in %s language.""",
                    customerMessage, language, agentResponse, mapLanguageName(language));

            String response = llmRouter.chat(SYSTEM_PROMPT, prompt);
            log.debug("AI response generated successfully");
            return response;

        } catch (Exception e) {
            log.warn("AI response generation failed, using raw agent response: {}", e.getMessage());
            return agentResponse;
        }
    }

    /**
     * Generate a natural language response from structured MCP data.
     *
     * @param customerMessage the original customer message
     * @param language        detected language code
     * @param mcpData         structured data from MCP/bank systems
     * @return natural language response
     */
    public String generate(String customerMessage, String language, Map<String, Object> mcpData) {
        return generate(customerMessage, language, mcpData.toString());
    }

    private String mapLanguageName(String code) {
        return switch (code) {
            case "hi" -> "Hindi";
            case "hi-en" -> "Hinglish (mix of Hindi and English)";
            case "ta" -> "Tamil";
            case "te" -> "Telugu";
            case "bn" -> "Bengali";
            case "mr" -> "Marathi";
            case "gu" -> "Gujarati";
            default -> "English";
        };
    }
}
