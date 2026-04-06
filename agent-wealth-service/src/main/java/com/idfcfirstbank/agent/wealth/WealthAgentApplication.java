package com.idfcfirstbank.agent.wealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Wealth Agent Service.
 * <p>
 * Handles wealth management operations including portfolio summaries,
 * SIP management, insurance status inquiries, and investment queries.
 * Uses Spring AI function calling to interact with MCP servers for
 * investment, insurance, and customer profile data.
 */
@SpringBootApplication
public class WealthAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(WealthAgentApplication.class, args);
    }
}
