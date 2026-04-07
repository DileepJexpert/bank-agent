package com.idfcfirstbank.agent.loans.config;

import com.idfcfirstbank.agent.common.config.LlmProviderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the Loan Agent Service.
 * <p>
 * Configures the Spring AI ChatClient with the active LLM provider and registers
 * loan-specific function callbacks for tool calling.
 * <p>
 * The same configurable LLM pattern as the orchestrator is used: switch provider
 * by changing {@code agent.llm.provider} in application.yml.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LoanAgentConfig {

    private final LlmProviderConfig llmProviderConfig;

    /**
     * Build a ChatClient wired to the chosen LLM provider with loan-specific system prompt.
     */
    @Bean
    public ChatClient chatClient(
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider,
            ChatClient.Builder builder) {

        String provider = llmProviderConfig.getProvider().toLowerCase();
        log.info("Loan Agent: Initialising ChatClient with provider={}, model={}",
                provider, llmProviderConfig.getModelName());

        ChatModel selectedModel = switch (provider) {
            case "anthropic" -> {
                AnthropicChatModel model = anthropicProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "Anthropic ChatModel not available. Check spring-ai-anthropic-spring-boot-starter "
                                    + "and spring.ai.anthropic.api-key.");
                }
                yield model;
            }
            case "openai", "ollama", "azure-openai", "mistral" -> {
                OpenAiChatModel model = openAiProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "OpenAI-compatible ChatModel not available. Check spring-ai-openai-spring-boot-starter "
                                    + "and the relevant spring.ai.openai.* properties.");
                }
                yield model;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported LLM provider: " + provider
                            + ". Supported: anthropic, openai, ollama, azure-openai, mistral");
        };

        return ChatClient.builder(selectedModel)
                .defaultSystem("""
                        You are the Loan Agent for IDFC First Bank. You handle loan-related queries
                        including loan eligibility checks, EMI calculations, prepayment scenarios,
                        repayment schedules, and general loan information.

                        You have access to the following tools to fulfill customer requests:
                        - checkEligibility: Check loan eligibility using credit bureau, account aggregator, and core banking data
                        - getLoanDetails: Retrieve existing loan details from the loan origination system
                        - calculateEmi: Calculate EMI for given principal, rate, and tenure
                        - calculatePrepayment: Calculate prepayment impact including new EMI and tenure reduction
                        - getRepaymentSchedule: Get the full repayment/amortization schedule for a loan

                        Always verify the customer's identity context before performing sensitive operations.
                        Be precise with financial figures and always confirm loan details before execution.
                        Format currency amounts in INR with proper formatting.
                        For prepayment on floating rate loans, note that RBI mandates zero prepayment charges.
                        """)
                .build();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
