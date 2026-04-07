package com.idfcfirstbank.agent.fraud.repository;

import com.idfcfirstbank.agent.fraud.entity.FraudEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudEventRepository extends JpaRepository<FraudEvent, UUID> {

    List<FraudEvent> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
