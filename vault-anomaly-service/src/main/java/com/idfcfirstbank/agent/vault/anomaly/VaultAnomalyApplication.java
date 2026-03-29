package com.idfcfirstbank.agent.vault.anomaly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VaultAnomalyApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultAnomalyApplication.class, args);
    }
}
