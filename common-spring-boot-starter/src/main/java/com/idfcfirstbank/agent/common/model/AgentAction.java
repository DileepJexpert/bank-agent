package com.idfcfirstbank.agent.common.model;

import java.util.Map;

/**
 * Represents an action performed by an agent in the platform.
 *
 * @param agentId      identifier of the agent performing the action
 * @param actionType   type of action (e.g. "BALANCE_INQUIRY", "FUND_TRANSFER")
 * @param customerId   identifier of the customer the action is performed for
 * @param resourceType type of resource being acted upon (e.g. "ACCOUNT", "LOAN")
 * @param resourceId   identifier of the specific resource
 * @param metadata     additional key-value pairs for extensibility
 */
public record AgentAction(
        String agentId,
        String actionType,
        String customerId,
        String resourceType,
        String resourceId,
        Map<String, Object> metadata
) {
}
