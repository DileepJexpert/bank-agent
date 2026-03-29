package com.idfcfirstbank.agent.vault.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id", nullable = false, unique = true)
    private String agentId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(name = "allowed_scopes", nullable = false)
    private String allowedScopes;

    @Column(name = "image_hash")
    private String imageHash;

    @Column(name = "agent_type", nullable = false)
    private String agentType;

    @Column(name = "allowed_mcp_servers")
    private String allowedMcpServers;

    @Column(name = "data_access_level", nullable = false)
    private String dataAccessLevel;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
