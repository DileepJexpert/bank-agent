package com.idfcfirstbank.agent.common.llm;

import java.util.Map;

/**
 * Defines a tool/function that can be passed to an LLM for function calling.
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {}
