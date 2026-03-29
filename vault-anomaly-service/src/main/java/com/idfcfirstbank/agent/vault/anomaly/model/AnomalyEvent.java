package com.idfcfirstbank.agent.vault.anomaly.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "anomaly_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyEvent {

    @Id
    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false, length = 50)
    private AnomalyType anomalyType;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "instance_id")
    private String instanceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AnomalyType {
        HIGH_RATE, UNKNOWN_ACTION, COORDINATED_ACCESS, PROMPT_INJECTION
    }
}
