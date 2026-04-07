package com.idfcfirstbank.agent.fraud.repository;

import com.idfcfirstbank.agent.fraud.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, String> {
}
