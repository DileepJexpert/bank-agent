package com.idfcfirstbank.agent.mcp.cardmgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the MCP Card Management Server.
 * <p>
 * Exposes card management operations as MCP-compatible tool endpoints with
 * resilience patterns (circuit breaker, retry, rate limiter) and Redis-based caching.
 * <p>
 * CRITICAL: This server handles card-sensitive data. Full card numbers must NEVER
 * appear in logs, responses, or cached data. Only the last 4 digits are permitted.
 */
@SpringBootApplication
@EnableCaching
public class McpCardManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpCardManagementApplication.class, args);
    }
}
