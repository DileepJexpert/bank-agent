package com.idfcfirstbank.agent.vault.audit.repository;

import com.idfcfirstbank.agent.vault.audit.model.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    Optional<AuditEventEntity> findByEventId(String eventId);

    Page<AuditEventEntity> findByAgentIdOrderByTimestampDesc(String agentId, Pageable pageable);

    Page<AuditEventEntity> findByCustomerIdOrderByTimestampDesc(String customerId, Pageable pageable);

    Page<AuditEventEntity> findByPolicyResultOrderByTimestampDesc(String policyResult, Pageable pageable);

    Page<AuditEventEntity> findByTimestampBetweenOrderByTimestampDesc(
            Instant start, Instant end, Pageable pageable);

    @Query("SELECT e FROM AuditEventEntity e WHERE "
            + "(:agentId IS NULL OR e.agentId = :agentId) AND "
            + "(:action IS NULL OR e.action = :action) AND "
            + "(:policyResult IS NULL OR e.policyResult = :policyResult) AND "
            + "(:from IS NULL OR e.timestamp >= :from) AND "
            + "(:to IS NULL OR e.timestamp <= :to) "
            + "ORDER BY e.timestamp DESC")
    Page<AuditEventEntity> findFiltered(
            @Param("agentId") String agentId,
            @Param("action") String action,
            @Param("policyResult") String policyResult,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT e.policyResult, COUNT(e) FROM AuditEventEntity e "
            + "WHERE e.timestamp >= :since GROUP BY e.policyResult")
    List<Object[]> countByPolicyResultSince(@Param("since") Instant since);

    @Query("SELECT e.agentId, COUNT(e) FROM AuditEventEntity e "
            + "WHERE e.timestamp >= :since GROUP BY e.agentId ORDER BY COUNT(e) DESC")
    List<Object[]> countByAgentIdSince(@Param("since") Instant since);

    long countByTimestampAfter(Instant since);

    @Query("SELECT e FROM AuditEventEntity e WHERE e.correlationId = :correlationId ORDER BY e.timestamp ASC")
    List<AuditEventEntity> findByCorrelationIdOrderByTimestampAsc(@Param("correlationId") UUID correlationId);
}
