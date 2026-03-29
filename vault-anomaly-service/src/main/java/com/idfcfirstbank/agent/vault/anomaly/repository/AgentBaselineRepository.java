package com.idfcfirstbank.agent.vault.anomaly.repository;

import com.idfcfirstbank.agent.vault.anomaly.model.AgentBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AgentBaselineRepository extends JpaRepository<AgentBaseline, String> {

    List<AgentBaseline> findByUpdatedAtAfter(Instant since);
}
