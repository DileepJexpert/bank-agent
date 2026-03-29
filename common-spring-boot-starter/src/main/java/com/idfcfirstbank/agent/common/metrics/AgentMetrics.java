package com.idfcfirstbank.agent.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Utility component for recording agent platform metrics.
 * Wraps the Micrometer {@link MeterRegistry} with domain-specific methods
 * that use consistent naming and tagging conventions for Grafana dashboards.
 */
@Component
@RequiredArgsConstructor
public class AgentMetrics {

    private static final String REQUEST_COUNT_METRIC = "agent.request.count";
    private static final String REQUEST_LATENCY_METRIC = "agent.request.latency";
    private static final String POLICY_EVAL_METRIC = "vault.policy.evaluation.time";

    private final MeterRegistry meterRegistry;

    /**
     * Increments the agent request counter.
     *
     * @param agentType the type of agent handling the request
     * @param action    the action being performed
     * @param status    the outcome status (e.g. "SUCCESS", "ERROR", "DENIED")
     */
    public void recordRequest(String agentType, String action, String status) {
        Counter.builder(REQUEST_COUNT_METRIC)
                .tag("agent_type", agentType)
                .tag("action", action)
                .tag("status", status)
                .description("Total number of agent requests")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records the latency of an agent request.
     *
     * @param agentType the type of agent handling the request
     * @param action    the action being performed
     * @param duration  the duration of the request
     */
    public void recordLatency(String agentType, String action, Duration duration) {
        Timer.builder(REQUEST_LATENCY_METRIC)
                .tag("agent_type", agentType)
                .tag("action", action)
                .description("Agent request latency")
                .register(meterRegistry)
                .record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Records the time taken for a vault policy evaluation.
     *
     * @param decision the policy decision outcome (e.g. "ALLOW", "DENY", "ESCALATE")
     * @param duration the duration of the policy evaluation
     */
    public void recordPolicyEvaluation(String decision, Duration duration) {
        Timer.builder(POLICY_EVAL_METRIC)
                .tag("decision", decision)
                .description("Vault policy evaluation time")
                .register(meterRegistry)
                .record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }
}
