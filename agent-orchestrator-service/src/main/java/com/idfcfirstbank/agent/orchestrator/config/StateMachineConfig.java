package com.idfcfirstbank.agent.orchestrator.config;

import com.idfcfirstbank.agent.orchestrator.statemachine.ConversationEvent;
import com.idfcfirstbank.agent.orchestrator.statemachine.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

/**
 * State machine definition for customer conversation lifecycle.
 * <p>
 * States flow: IDLE -> AUTHENTICATING -> DETECTING_INTENT -> ROUTING ->
 * PROCESSING -> RESPONDING -> (back to DETECTING_INTENT or COMPLETED).
 * ESCALATING can be reached from any processing state.
 */
@Slf4j
@Configuration
@EnableStateMachineFactory
public class StateMachineConfig extends EnumStateMachineConfigurerAdapter<ConversationState, ConversationEvent> {

    @Override
    public void configure(StateMachineConfigurationConfigurer<ConversationState, ConversationEvent> config)
            throws Exception {
        config.withConfiguration()
                .autoStartup(false)
                .listener(new StateMachineListenerAdapter<>() {
                    @Override
                    public void stateChanged(State<ConversationState, ConversationEvent> from,
                                             State<ConversationState, ConversationEvent> to) {
                        log.info("Conversation state transition: {} -> {}",
                                from != null ? from.getId() : "NONE", to.getId());
                    }
                });
    }

    @Override
    public void configure(StateMachineStateConfigurer<ConversationState, ConversationEvent> states)
            throws Exception {
        states.withStates()
                .initial(ConversationState.IDLE)
                .end(ConversationState.COMPLETED)
                .states(EnumSet.allOf(ConversationState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ConversationState, ConversationEvent> transitions)
            throws Exception {
        transitions
                // Customer connects -> begin authentication
                .withExternal()
                    .source(ConversationState.IDLE)
                    .target(ConversationState.AUTHENTICATING)
                    .event(ConversationEvent.CUSTOMER_CONNECTED)
                    .and()
                // Authentication complete -> detect intent
                .withExternal()
                    .source(ConversationState.AUTHENTICATING)
                    .target(ConversationState.DETECTING_INTENT)
                    .event(ConversationEvent.AUTHENTICATED)
                    .and()
                // Intent detected -> route to agent
                .withExternal()
                    .source(ConversationState.DETECTING_INTENT)
                    .target(ConversationState.ROUTING)
                    .event(ConversationEvent.INTENT_DETECTED)
                    .and()
                // Agent selected -> process request
                .withExternal()
                    .source(ConversationState.ROUTING)
                    .target(ConversationState.PROCESSING)
                    .event(ConversationEvent.AGENT_SELECTED)
                    .and()
                // Agent responded -> send response
                .withExternal()
                    .source(ConversationState.PROCESSING)
                    .target(ConversationState.RESPONDING)
                    .event(ConversationEvent.AGENT_RESPONDED)
                    .and()
                // Response sent -> back to detecting intent for follow-up
                .withExternal()
                    .source(ConversationState.RESPONDING)
                    .target(ConversationState.DETECTING_INTENT)
                    .event(ConversationEvent.INTENT_DETECTED)
                    .and()
                // Escalation from processing
                .withExternal()
                    .source(ConversationState.PROCESSING)
                    .target(ConversationState.ESCALATING)
                    .event(ConversationEvent.ESCALATION_REQUIRED)
                    .and()
                // Escalation from routing (no suitable agent)
                .withExternal()
                    .source(ConversationState.ROUTING)
                    .target(ConversationState.ESCALATING)
                    .event(ConversationEvent.ESCALATION_REQUIRED)
                    .and()
                // Session end from any conversational state
                .withExternal()
                    .source(ConversationState.RESPONDING)
                    .target(ConversationState.COMPLETED)
                    .event(ConversationEvent.SESSION_ENDED)
                    .and()
                .withExternal()
                    .source(ConversationState.DETECTING_INTENT)
                    .target(ConversationState.COMPLETED)
                    .event(ConversationEvent.SESSION_ENDED)
                    .and()
                .withExternal()
                    .source(ConversationState.ESCALATING)
                    .target(ConversationState.COMPLETED)
                    .event(ConversationEvent.SESSION_ENDED);
    }
}
