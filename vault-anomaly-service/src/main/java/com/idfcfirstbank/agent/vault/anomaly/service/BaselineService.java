package com.idfcfirstbank.agent.vault.anomaly.service;

import com.idfcfirstbank.agent.vault.anomaly.model.AgentBaseline;
import com.idfcfirstbank.agent.vault.anomaly.repository.AgentBaselineRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages agent behavioral baselines for anomaly detection.
 * <p>
 * Loads baselines from the database on startup and periodically refreshes
 * them (every hour by default). Provides methods to check whether observed
 * behavior deviates from established baselines.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineService {

    private final AgentBaselineRepository baselineRepository;

    @Value("${agent.anomaly.baseline-update-interval-minutes:60}")
    private int baselineUpdateIntervalMinutes;

    /**
     * In-memory cache of agent baselines, keyed by agent type.
     */
    private final Map<String, AgentBaseline> baselineCache = new ConcurrentHashMap<>();

    @PostConstruct
    void loadBaselines() {
        refreshBaselines();
        log.info("Loaded {} agent baselines on startup", baselineCache.size());
    }

    /**
     * Periodically refreshes baselines from the database.
     * Runs every hour (configurable via agent.anomaly.baseline-update-interval-minutes).
     */
    @Scheduled(fixedDelayString = "#{${agent.anomaly.baseline-update-interval-minutes:60} * 60000}")
    public void refreshBaselines() {
        try {
            List<AgentBaseline> baselines = baselineRepository.findAll();
            baselineCache.clear();
            for (AgentBaseline baseline : baselines) {
                baselineCache.put(baseline.getAgentType(), baseline);
            }
            log.debug("Refreshed {} agent baselines from database", baselines.size());
        } catch (Exception e) {
            log.error("Failed to refresh agent baselines: {}", e.getMessage(), e);
        }
    }

    /**
     * Determines if the given action count for an agent type within a time window
     * is anomalous based on the established baseline.
     * <p>
     * An action count is considered anomalous if it exceeds the mean + 3 standard
     * deviations of the baseline (scaled to the given time window).
     *
     * @param agentType       the type of agent
     * @param actionCount     the observed action count
     * @param timeWindowMinutes the time window in minutes over which actions were counted
     * @return true if the behavior is anomalous, false otherwise
     */
    public boolean isAnomalous(String agentType, long actionCount, int timeWindowMinutes) {
        AgentBaseline baseline = baselineCache.get(agentType);
        if (baseline == null) {
            // No baseline exists -- cannot determine anomaly, err on the side of caution
            log.debug("No baseline found for agentType={}, treating as non-anomalous", agentType);
            return false;
        }

        // Scale baseline rate to the requested time window
        double expectedCount = baseline.getAvgRequestsPerMin() * timeWindowMinutes;
        double scaledStddev = baseline.getStddev() * Math.sqrt(timeWindowMinutes);

        // Anomalous if exceeding mean + 3 standard deviations
        double threshold = expectedCount + (3 * scaledStddev);
        boolean anomalous = actionCount > threshold;

        if (anomalous) {
            log.debug("Anomalous behavior detected: agentType={}, actionCount={}, threshold={}",
                    agentType, actionCount, String.format("%.2f", threshold));
        }

        return anomalous;
    }

    /**
     * Checks if a given action type is known for the specified agent type.
     *
     * @param agentType  the type of agent
     * @param actionType the action type to check
     * @return true if the action is in the known baseline, false if unknown
     */
    public boolean isKnownAction(String agentType, String actionType) {
        AgentBaseline baseline = baselineCache.get(agentType);
        if (baseline == null || baseline.getCommonActionTypes() == null) {
            // No baseline exists -- treat as known to avoid false positives
            return true;
        }
        return baseline.getCommonActionTypes().contains(actionType);
    }

    /**
     * Returns the current baseline for a specific agent type.
     */
    public Optional<AgentBaseline> getBaseline(String agentType) {
        return Optional.ofNullable(baselineCache.get(agentType));
    }

    /**
     * Returns all currently cached baselines.
     */
    public Map<String, AgentBaseline> getAllBaselines() {
        return Collections.unmodifiableMap(baselineCache);
    }

    /**
     * Updates or creates a baseline for the given agent type.
     * Persists to the database and updates the in-memory cache.
     */
    public AgentBaseline updateBaseline(String agentType, double avgRequestsPerMin,
                                        double stddev, List<String> commonActionTypes) {
        AgentBaseline baseline = baselineCache.getOrDefault(agentType,
                AgentBaseline.builder().agentType(agentType).build());

        baseline.setAvgRequestsPerMin(avgRequestsPerMin);
        baseline.setStddev(stddev);
        baseline.setCommonActionTypes(commonActionTypes);
        baseline.setUpdatedAt(Instant.now());

        AgentBaseline saved = baselineRepository.save(baseline);
        baselineCache.put(agentType, saved);

        log.info("Updated baseline for agentType={}: avgReq/min={}, stddev={}, actions={}",
                agentType, avgRequestsPerMin, stddev, commonActionTypes.size());

        return saved;
    }
}
