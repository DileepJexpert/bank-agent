package com.idfcfirstbank.agent.collections.repository;

import com.idfcfirstbank.agent.collections.entity.CollectionsInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing collections interaction records.
 * Provides query methods to retrieve a customer's interaction history in reverse chronological order.
 */
@Repository
public interface CollectionsInteractionRepository extends JpaRepository<CollectionsInteraction, UUID> {

    /**
     * Find all interactions for a given customer, ordered by call timestamp descending
     * (most recent first).
     *
     * @param customerId the customer identifier
     * @return list of interactions ordered by call timestamp descending
     */
    List<CollectionsInteraction> findByCustomerIdOrderByCallTimestampDesc(String customerId);
}
