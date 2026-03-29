package com.idfcfirstbank.agent.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer metrics configuration for the agent platform.
 * Registers common tags and pre-defined meters for agent request tracking,
 * latency measurement, and vault policy evaluation timing.
 */
@Configuration
public class AgentMetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> agentMeterRegistryCustomizer(
            @Value("${spring.application.name:agent-platform}") String applicationName,
            @Value("${agent.type:UNKNOWN}") String agentType) {
        return registry -> registry.config()
                .commonTags("application", applicationName, "agent_type", agentType);
    }
}
