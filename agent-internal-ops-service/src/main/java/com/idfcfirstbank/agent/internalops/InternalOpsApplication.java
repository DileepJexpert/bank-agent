package com.idfcfirstbank.agent.internalops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Internal Ops Agent Service.
 * <p>
 * Serves bank employees with MIS reports, reconciliation status,
 * compliance queries, IT helpdesk, and HR queries.
 * Uses Spring Batch for report generation jobs.
 */
@SpringBootApplication
public class InternalOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(InternalOpsApplication.class, args);
    }
}
