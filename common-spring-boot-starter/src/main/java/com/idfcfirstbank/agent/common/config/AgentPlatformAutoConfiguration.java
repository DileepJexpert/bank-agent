package com.idfcfirstbank.agent.common.config;

import com.idfcfirstbank.agent.common.audit.AuditAspect;
import com.idfcfirstbank.agent.common.exception.GlobalExceptionHandler;
import com.idfcfirstbank.agent.common.health.KafkaHealthIndicator;
import com.idfcfirstbank.agent.common.health.VaultHealthIndicator;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.metrics.AgentMetrics;
import com.idfcfirstbank.agent.common.metrics.AgentMetricsConfig;
import com.idfcfirstbank.agent.common.security.JwtTokenProvider;
import com.idfcfirstbank.agent.common.security.ServiceAuthenticationFilter;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration entry point for the Agent Platform common library.
 * Automatically registers all shared beans, filters, and configuration properties
 * when this starter is on the classpath.
 */
@AutoConfiguration
@Import({
        KafkaCommonConfig.class,
        JwtTokenProvider.class,
        ServiceAuthenticationFilter.class,
        VaultClient.class,
        AuditEventPublisher.class,
        GlobalExceptionHandler.class,
        AuditAspect.class,
        AgentMetricsConfig.class,
        AgentMetrics.class
})
@ComponentScan(basePackages = {
        "com.idfcfirstbank.agent.common",
        "com.idfcfirstbank.agent.common.audit",
        "com.idfcfirstbank.agent.common.health",
        "com.idfcfirstbank.agent.common.metrics"
})
public class AgentPlatformAutoConfiguration {
}
