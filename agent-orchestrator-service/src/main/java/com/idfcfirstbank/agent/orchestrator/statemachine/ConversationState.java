package com.idfcfirstbank.agent.orchestrator.statemachine;

/**
 * States in the customer conversation lifecycle state machine.
 */
public enum ConversationState {
    IDLE,
    AUTHENTICATING,
    DETECTING_INTENT,
    ROUTING,
    PROCESSING,
    RESPONDING,
    ESCALATING,
    COMPLETED
}
