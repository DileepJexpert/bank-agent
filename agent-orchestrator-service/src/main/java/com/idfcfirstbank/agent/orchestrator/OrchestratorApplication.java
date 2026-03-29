package com.idfcfirstbank.agent.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Agent Orchestrator Service.
 * <p>
 * Responsible for:
 * <ul>
 *   <li>Customer intent detection (tiered: keyword, small model, large LLM)</li>
 *   <li>Routing requests to the appropriate domain agent</li>
 *   <li>Conversation session management backed by Redis</li>
 *   <li>WebSocket-based real-time chat and REST API</li>
 * </ul>
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
