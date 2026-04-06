package com.idfcfirstbank.agent.collections.repository;

import com.idfcfirstbank.agent.collections.entity.CollectionsQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing the collections queue.
 * Provides methods to query overdue accounts by status and priority for batch processing.
 */
@Repository
public interface CollectionsQueueRepository extends JpaRepository<CollectionsQueue, UUID> {

    /**
     * Find all queued accounts with the given status, ordered by priority descending
     * so that the highest-priority accounts are processed first.
     *
     * @param status the queue status to filter by (e.g., PENDING, IN_PROGRESS, COMPLETED)
     * @return list of queued accounts ordered by priority descending
     */
    List<CollectionsQueue> findByStatusOrderByPriorityDesc(String status);
}
