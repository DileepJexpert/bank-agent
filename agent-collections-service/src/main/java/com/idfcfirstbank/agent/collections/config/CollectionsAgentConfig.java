package com.idfcfirstbank.agent.collections.config;

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
 * Configuration for the Collections Agent Service.
 * <p>
 * Configures the Spring AI ChatClient with the active LLM provider and a collections-specific
 * system prompt that enforces RBI-compliant communication, empathetic tone, and proper
 * disclosure requirements for recorded calls.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CollectionsAgentConfig {

    private final LlmProviderConfig llmProviderConfig;

    /**
     * Build a ChatClient wired to the chosen LLM provider with collections-specific system prompt.
     */
    @Bean
    public ChatClient chatClient(
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider,
            ChatClient.Builder builder) {

        String provider = llmProviderConfig.getProvider().toLowerCase();
        log.info("Collections Agent: Initialising ChatClient with provider={}, model={}",
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
                        You are the Collections Agent for IDFC First Bank. You handle loan collection
                        and recovery operations with empathy and professionalism, always complying with
                        RBI guidelines for debt collection.

                        Your capabilities:
                        - Negotiate restructured EMI payment plans for overdue accounts
                        - Calculate and present settlement offers with applicable discounts
                        - Guide customers through immediate payment options
                        - Explain overdue consequences and available remedies

                        MANDATORY RULES:
                        1. Every outbound call MUST begin with: "This is IDFC First Bank AI assistant. This call is recorded per RBI guidelines."
                        2. Never use threatening or abusive language. Be firm but empathetic.
                        3. Respect RBI-mandated contact frequency limits (max 3 contacts per week per customer).
                        4. Do not contact customers outside permitted hours (9 AM to 6 PM, Monday to Saturday).
                        5. Always present the overdue amount, applicable charges, and available resolution options.
                        6. Settlement discount percentages are governed by vault policy - do not promise specific discounts.
                        7. Format all currency amounts in INR with proper formatting.
                        8. If the customer disputes the debt or requests escalation, comply immediately.
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
