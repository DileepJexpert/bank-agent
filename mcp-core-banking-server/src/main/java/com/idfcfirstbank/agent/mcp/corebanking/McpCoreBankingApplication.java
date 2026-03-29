package com.idfcfirstbank.agent.mcp.corebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the MCP Core Banking Server.
 * <p>
 * Exposes core banking operations (backed by Finacle) as MCP-compatible tool
 * endpoints with resilience patterns (circuit breaker, retry, rate limiter)
 * and Redis-based caching.
 */
@SpringBootApplication
@EnableCaching
public class McpCoreBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpCoreBankingApplication.class, args);
    }
}
