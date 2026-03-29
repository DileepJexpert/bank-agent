package com.idfcfirstbank.agent.card;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Card Agent Service.
 * <p>
 * Handles card-related banking operations including card blocking, activation,
 * reward points inquiry, dispute raising, and EMI conversion. Uses Spring AI
 * function calling to interact with the MCP Card Management Server.
 * <p>
 * Supports PCI-DSS profile for enhanced security in production environments
 * where card data masking, secure headers, and cache restrictions are enforced.
 */
@SpringBootApplication
public class CardAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardAgentApplication.class, args);
    }
}
