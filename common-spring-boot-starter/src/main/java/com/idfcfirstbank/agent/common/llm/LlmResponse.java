package com.idfcfirstbank.agent.common.llm;

import java.util.List;

/**
 * Structured response from any LLM provider, including token usage and tool calls.
 */
public record LlmResponse(
        String content,
        String model,
        String provider,
        int inputTokens,
        int outputTokens,
        List<ToolCall> toolCalls
) {
    public record ToolCall(String name, String arguments) {}

    /** Convenience factory for plain text responses. */
    public static LlmResponse of(String content, String model, String provider) {
        return new LlmResponse(content, model, provider, 0, 0, List.of());
    }
}
