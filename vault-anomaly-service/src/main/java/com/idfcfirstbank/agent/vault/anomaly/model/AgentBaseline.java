package com.idfcfirstbank.agent.vault.anomaly.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.List;

@Entity
@Table(name = "agent_baselines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentBaseline {

    @Id
    @Column(name = "agent_type", nullable = false)
    private String agentType;

    @Column(name = "avg_requests_per_min", nullable = false)
    private double avgRequestsPerMin;

    @Column(name = "stddev", nullable = false)
    private double stddev;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "common_action_types", nullable = false, columnDefinition = "jsonb")
    private List<String> commonActionTypes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
