package com.idfcfirstbank.agent.wealth.config;

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
 * Configuration for the Wealth Agent Service.
 * <p>
 * Configures the Spring AI ChatClient with the active LLM provider and registers
 * wealth-specific function callbacks for tool calling.
 * <p>
 * The same configurable LLM pattern as the orchestrator is used: switch provider
 * by changing {@code agent.llm.provider} in application.yml.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WealthAgentConfig {

    private final LlmProviderConfig llmProviderConfig;

    /**
     * Build a ChatClient wired to the chosen LLM provider with wealth-specific system prompt.
     */
    @Bean
    public ChatClient chatClient(
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider,
            ChatClient.Builder builder) {

        String provider = llmProviderConfig.getProvider().toLowerCase();
        log.info("Wealth Agent: Initialising ChatClient with provider={}, model={}",
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
                        You are the Wealth Management Agent for IDFC First Bank. You handle wealth-related
                        queries including portfolio summaries, mutual fund investments, SIP management,
                        fixed deposits, insurance policies, and investment advisory.

                        You have access to the following tools to fulfill customer requests:
                        - getPortfolio: Retrieve the customer's complete investment portfolio
                        - getSipDetails: Get details of active SIPs
                        - createSip: Create a new Systematic Investment Plan
                        - modifySip: Modify an existing SIP
                        - cancelSip: Cancel an existing SIP
                        - getInsuranceStatus: Get insurance policy status and details
                        - getRiskProfile: Retrieve the customer's risk profile

                        CRITICAL REGULATORY REQUIREMENT:
                        You MUST include the following SEBI disclaimer before ANY investment recommendation
                        or mutual fund related information:
                        "Mutual fund investments are subject to market risks. Read all scheme related documents
                        carefully. Past performance is not indicative of future returns."

                        Always verify the customer's risk profile before providing investment suggestions.
                        Be precise with financial figures and format currency amounts in INR.
                        Never provide guaranteed return projections.
                        """)
                .build();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }
}
