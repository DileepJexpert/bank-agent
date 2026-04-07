package com.idfcfirstbank.agent.orchestrator.config;

import com.idfcfirstbank.agent.common.config.LlmProviderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Spring AI {@link ChatClient} bean based on the active LLM provider.
 * <p>
 * The provider is selected via {@code agent.llm.provider} in application.yml.
 * Supported providers: {@code anthropic}, {@code openai}, {@code ollama},
 * {@code azure-openai}, {@code mistral}.
 * <p>
 * Ollama uses its dedicated {@link OllamaChatModel} when selected.
 * Azure OpenAI and Mistral use the OpenAI-compatible starter under the hood.
 * <p>
 * To switch providers, change the YAML property and supply the matching API key / base-url.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmConfig {

    private final LlmProviderConfig llmProviderConfig;

    /**
     * Build a {@link ChatClient} wired to whichever {@link ChatModel} the chosen
     * provider auto-configured.
     */
    @Bean
    public ChatClient chatClient(
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OpenAiChatModel> openAiProvider,
            ObjectProvider<OllamaChatModel> ollamaProvider) {

        String provider = llmProviderConfig.getProvider().toLowerCase();
        log.info("Initialising ChatClient with LLM provider: {}, model: {}",
                provider, llmProviderConfig.getModelName());

        ChatModel selectedModel = switch (provider) {
            case "anthropic" -> {
                AnthropicChatModel model = anthropicProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "Anthropic ChatModel not available. Ensure spring-ai-anthropic-spring-boot-starter "
                                    + "is on the classpath and spring.ai.anthropic.api-key is set.");
                }
                yield model;
            }
            case "ollama" -> {
                OllamaChatModel model = ollamaProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "Ollama ChatModel not available. Ensure spring-ai-ollama-spring-boot-starter "
                                    + "is on the classpath and spring.ai.ollama.base-url is set.");
                }
                log.info("Using Ollama ChatModel with base-url from spring.ai.ollama config");
                yield model;
            }
            case "openai", "azure-openai", "mistral" -> {
                OpenAiChatModel model = openAiProvider.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "OpenAI-compatible ChatModel not available. Ensure spring-ai-openai-spring-boot-starter "
                                    + "is on the classpath and the relevant spring.ai.openai.* properties are set.");
                }
                yield model;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported LLM provider: " + provider
                            + ". Supported: anthropic, openai, ollama, azure-openai, mistral");
        };

        return ChatClient.builder(selectedModel)
                .defaultSystem("You are a helpful banking assistant for IDFC First Bank. "
                        + "You help customers with account inquiries, transactions, and general banking queries. "
                        + "Always be polite, precise, and security-conscious. Never reveal sensitive information "
                        + "without proper authentication.")
                .build();
    }
}
