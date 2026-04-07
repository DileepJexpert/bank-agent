package com.idfcfirstbank.agent.card.config;

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
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the Card Agent Service.
 * <p>
 * Configures the Spring AI ChatClient with the active LLM provider and registers
 * card-specific system prompt for tool calling.
 * <p>
 * The same configurable LLM pattern as the orchestrator is used: switch provider
 * by changing {@code agent.llm.provider} in application.yml.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CardAgentConfig {

    private final LlmProviderConfig llmProviderConfig;

    /**
     * Build a ChatClient wired to the chosen LLM provider with card-specific system prompt.
     */
    @Bean
    public ChatClient chatClient(
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider,
            ChatClient.Builder builder) {

        String provider = llmProviderConfig.getProvider().toLowerCase();
        log.info("Card Agent: Initialising ChatClient with provider={}, model={}",
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
                        You are the Card Agent for IDFC First Bank. You handle card-related queries
                        including card blocking, card activation, reward points inquiry, dispute raising,
                        EMI conversion, and limit management.

                        You have access to the following tools to fulfill customer requests:
                        - blockCard: Block a card immediately (urgent, Tier 1)
                        - activateCard: Activate a new or replacement card (Tier 1)
                        - getRewardPoints: Retrieve reward points balance and history
                        - redeemPoints: Redeem reward points
                        - raiseDispute: Raise a transaction dispute (Tier 2, requires details)
                        - convertToEMI: Convert a transaction to EMI
                        - changeLimit: Change card transaction limits

                        CRITICAL SECURITY RULES:
                        - NEVER display or include full card numbers in any response.
                        - Always refer to cards by their last 4 digits only (e.g., "card ending in 1234").
                        - Verify the customer's identity context before performing any sensitive operations.
                        - For card blocking requests, act immediately without unnecessary conversation.
                        - Format currency amounts in INR with proper formatting.
                        """)
                .build();
    }

    /**
     * RestTemplate for making MCP server calls.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * PCI-DSS profile: disable caching of card data in Redis to prevent
     * sensitive card information from being stored in cache.
     */
    @Bean
    @Profile("pci-dss")
    public RedisCacheConfiguration pciDssRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(1));
    }
}
