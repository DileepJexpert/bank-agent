package com.idfcfirstbank.agent.orchestrator.statemachine;

/**
 * Events that drive transitions in the customer conversation state machine.
 */
public enum ConversationEvent {
    CUSTOMER_CONNECTED,
    AUTHENTICATED,
    INTENT_DETECTED,
    AGENT_SELECTED,
    AGENT_RESPONDED,
    ESCALATION_REQUIRED,
    SESSION_ENDED
}
