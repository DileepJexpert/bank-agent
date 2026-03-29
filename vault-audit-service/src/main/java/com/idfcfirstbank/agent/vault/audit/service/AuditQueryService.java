package com.idfcfirstbank.agent.vault.audit.service;

import com.idfcfirstbank.agent.vault.audit.model.AuditEventEntity;
import com.idfcfirstbank.agent.vault.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditQueryService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Queries audit events with optional filters.
     */
    public Page<AuditEventEntity> queryEvents(String agentId, String action,
                                               String policyResult, Instant from,
                                               Instant to, Pageable pageable) {
        return auditEventRepository.findFiltered(agentId, action, policyResult, from, to, pageable);
    }

    /**
     * Gets a single audit event by its event ID.
     */
    public Optional<AuditEventEntity> getByEventId(String eventId) {
        return auditEventRepository.findByEventId(eventId);
    }

    /**
     * Gets paginated events for a specific agent.
     */
    public Page<AuditEventEntity> getEventsByAgent(String agentId, Pageable pageable) {
        return auditEventRepository.findByAgentIdOrderByTimestampDesc(agentId, pageable);
    }

    /**
     * Gets all audit events sharing the same correlation ID, ordered chronologically.
     * This traces a full customer interaction across multiple agents and actions.
     */
    public List<AuditEventEntity> getEventsByCorrelationId(UUID correlationId) {
        return auditEventRepository.findByCorrelationIdOrderByTimestampAsc(correlationId);
    }

    /**
     * Generates aggregate audit statistics for the given time window.
     */
    public Map<String, Object> getStatistics(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Map<String, Object> stats = new HashMap<>();

        // Total event count
        long totalEvents = auditEventRepository.countByTimestampAfter(since);
        stats.put("totalEvents", totalEvents);
        stats.put("windowHours", hours);
        stats.put("since", since.toString());

        // Decision breakdown
        List<Object[]> decisionCounts = auditEventRepository.countByPolicyResultSince(since);
        Map<String, Long> decisions = new LinkedHashMap<>();
        for (Object[] row : decisionCounts) {
            decisions.put((String) row[0], (Long) row[1]);
        }
        stats.put("decisionBreakdown", decisions);

        // Agent activity
        List<Object[]> agentCounts = auditEventRepository.countByAgentIdSince(since);
        Map<String, Long> agentActivity = new LinkedHashMap<>();
        for (Object[] row : agentCounts) {
            agentActivity.put((String) row[0], (Long) row[1]);
        }
        stats.put("agentActivity", agentActivity);

        // Deny rate
        long denyCount = decisions.getOrDefault("DENY", 0L);
        double denyRate = totalEvents > 0 ? (double) denyCount / totalEvents * 100.0 : 0.0;
        stats.put("denyRatePercent", Math.round(denyRate * 100.0) / 100.0);

        // Escalation count
        long escalationCount = decisions.getOrDefault("ESCALATE", 0L);
        stats.put("escalationCount", escalationCount);

        return stats;
    }
}
