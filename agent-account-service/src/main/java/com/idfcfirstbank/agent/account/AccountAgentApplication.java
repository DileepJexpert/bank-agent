package com.idfcfirstbank.agent.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Account Agent Service.
 * <p>
 * Handles account-related banking operations including balance inquiry,
 * transaction history, cheque book requests, fixed deposit creation,
 * and fund transfers. Uses Spring AI function calling to interact with
 * the MCP Core Banking Server.
 */
@SpringBootApplication
public class AccountAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountAgentApplication.class, args);
    }
}
