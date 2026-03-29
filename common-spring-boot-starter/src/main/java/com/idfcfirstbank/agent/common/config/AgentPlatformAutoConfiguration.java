package com.idfcfirstbank.agent.common.config;

import com.idfcfirstbank.agent.common.exception.GlobalExceptionHandler;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.security.JwtTokenProvider;
import com.idfcfirstbank.agent.common.security.ServiceAuthenticationFilter;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration entry point for the Agent Platform common library.
 * Automatically registers all shared beans, filters, and configuration properties
 * when this starter is on the classpath.
 */
@AutoConfiguration
@EnableConfigurationProperties(LlmProviderConfig.class)
@Import({
        KafkaCommonConfig.class,
        JwtTokenProvider.class,
        ServiceAuthenticationFilter.class,
        VaultClient.class,
        AuditEventPublisher.class,
        GlobalExceptionHandler.class
})
@ComponentScan(basePackages = "com.idfcfirstbank.agent.common")
public class AgentPlatformAutoConfiguration {
}
