package com.idfcfirstbank.agent.internalops.config;

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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class InternalOpsConfig {

    private final LlmProviderConfig llmProviderConfig;

    @Bean
    public ChatClient chatClient(
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider,
            ChatClient.Builder builder) {

        String provider = llmProviderConfig.getProvider().toLowerCase();
        log.info("Internal Ops Agent: Initialising ChatClient with provider={}, model={}",
                provider, llmProviderConfig.getModelName());

        ChatModel selectedModel = switch (provider) {
            case "anthropic" -> {
                AnthropicChatModel model = anthropicProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "Anthropic ChatModel not available. Check spring-ai-anthropic-spring-boot-starter and API key.");
                }
                yield model;
            }
            case "openai", "ollama", "azure-openai", "mistral" -> {
                OpenAiChatModel model = openAiProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "OpenAI-compatible ChatModel not available. Check spring-ai-openai-spring-boot-starter.");
                }
                yield model;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported LLM provider: " + provider);
        };

        return ChatClient.builder(selectedModel)
                .defaultSystem("""
                        You are the Internal Operations Agent for IDFC First Bank. You serve bank employees
                        (not customers). You handle:
                        - MIS Report generation (daily transaction summaries by branch)
                        - Reconciliation status queries
                        - Compliance queries (KYC renewals, regulatory checks)
                        - IT Helpdesk queries (password resets, system access)
                        - HR queries (leave balance, policies)

                        Always maintain a professional tone suitable for internal communications.
                        Provide accurate, structured data in your responses.
                        Flag any compliance concerns immediately.
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
