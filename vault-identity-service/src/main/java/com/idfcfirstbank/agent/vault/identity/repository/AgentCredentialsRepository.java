package com.idfcfirstbank.agent.vault.identity.repository;

import com.idfcfirstbank.agent.vault.identity.model.AgentCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentCredentialsRepository extends JpaRepository<AgentCredentials, UUID> {

    Optional<AgentCredentials> findByAgentIdAndActiveTrue(String agentId);

    Optional<AgentCredentials> findByServiceNameAndActiveTrue(String serviceName);

    boolean existsByAgentIdAndActiveTrue(String agentId);
}
