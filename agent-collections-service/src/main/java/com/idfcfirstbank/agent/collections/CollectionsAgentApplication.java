package com.idfcfirstbank.agent.collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Collections Agent Service.
 * <p>
 * Handles loan collection operations including inbound customer calls for overdue accounts,
 * payment plan restructuring, settlement offer negotiation, and outbound batch collection
 * campaigns. Uses Spring AI for conversational negotiation and Agentic Vault for policy
 * enforcement (RBI contact frequency limits, discount thresholds, calling-hour restrictions).
 */
@SpringBootApplication
@EnableScheduling
public class CollectionsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectionsAgentApplication.class, args);
    }
}
