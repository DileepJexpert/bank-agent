package com.idfcfirstbank.agent.common.llm;

import java.util.Map;

/**
 * Defines a tool/function that can be passed to an LLM for function calling.
 * The parameters map is defensively copied to ensure immutability.
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {
    public ToolDefinition {
        parameters = Map.copyOf(parameters);
    }
}
