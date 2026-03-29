package com.idfcfirstbank.agent.vault.anomaly.repository;

import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.AnomalyType;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AnomalyEventRepository extends JpaRepository<AnomalyEvent, UUID> {

    Page<AnomalyEvent> findBySeverity(Severity severity, Pageable pageable);

    Page<AnomalyEvent> findByAnomalyType(AnomalyType anomalyType, Pageable pageable);

    Page<AnomalyEvent> findByAgentId(String agentId, Pageable pageable);

    @Query("SELECT e FROM AnomalyEvent e WHERE e.detectedAt BETWEEN :from AND :to")
    Page<AnomalyEvent> findByDateRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("SELECT e FROM AnomalyEvent e WHERE e.resolvedAt IS NULL ORDER BY e.detectedAt DESC")
    Page<AnomalyEvent> findUnresolved(Pageable pageable);

    @Query("""
            SELECT e FROM AnomalyEvent e
            WHERE (:severity IS NULL OR e.severity = :severity)
              AND (:anomalyType IS NULL OR e.anomalyType = :anomalyType)
              AND (:agentId IS NULL OR e.agentId = :agentId)
              AND (:from IS NULL OR e.detectedAt >= :from)
              AND (:to IS NULL OR e.detectedAt <= :to)
            ORDER BY e.detectedAt DESC
            """)
    Page<AnomalyEvent> findWithFilters(
            @Param("severity") Severity severity,
            @Param("anomalyType") AnomalyType anomalyType,
            @Param("agentId") String agentId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    long countBySeverityAndResolvedAtIsNull(Severity severity);
}
