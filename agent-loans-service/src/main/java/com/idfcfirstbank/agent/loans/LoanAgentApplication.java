package com.idfcfirstbank.agent.loans;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Loan Agent Service.
 * <p>
 * Handles loan-related banking operations including eligibility checks,
 * EMI queries, prepayment calculations, and general loan inquiries.
 * Uses Spring AI function calling to interact with credit bureau,
 * account aggregator, loan origination, and core banking MCP servers.
 */
@SpringBootApplication
public class LoanAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanAgentApplication.class, args);
    }
}
