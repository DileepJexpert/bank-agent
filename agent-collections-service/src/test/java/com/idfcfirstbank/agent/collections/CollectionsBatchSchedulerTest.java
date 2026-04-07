package com.idfcfirstbank.agent.collections;

import com.idfcfirstbank.agent.collections.entity.CollectionsQueue;
import com.idfcfirstbank.agent.collections.repository.CollectionsInteractionRepository;
import com.idfcfirstbank.agent.collections.repository.CollectionsQueueRepository;
import com.idfcfirstbank.agent.collections.scheduler.CollectionsBatchScheduler;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CollectionsBatchScheduler}.
 * Tests the batch processing logic with mocked Redis and VaultClient.
 */
@ExtendWith(MockitoExtension.class)
class CollectionsBatchSchedulerTest {

    @Mock
    private CollectionsQueueRepository queueRepository;

    @Mock
    private CollectionsInteractionRepository interactionRepository;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CollectionsBatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "instanceId", "agent-collections-service-test");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private CollectionsQueue buildQueueEntry(String customerId, int daysOverdue, int priority) {
        return CollectionsQueue.builder()
                .queueId(UUID.randomUUID())
                .customerId(customerId)
                .productType("PERSONAL_LOAN")
                .overdueAmount(new BigDecimal("25000.00"))
                .daysOverdue(daysOverdue)
                .priority(priority)
                .status("PENDING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PolicyDecision allowDecision() {
        return new PolicyDecision(PolicyDecision.Decision.ALLOW, "Allowed", "POLICY-001");
    }

    private PolicyDecision denyDecision() {
        return new PolicyDecision(PolicyDecision.Decision.DENY, "Outside permitted hours", "POLICY-002");
    }

    @Test
    @DisplayName("Should process all pending accounts when vault allows and contact limit not reached")
    void shouldProcessAllPendingAccounts() {
        List<CollectionsQueue> pendingAccounts = List.of(
                buildQueueEntry("CUST-001", 30, 5),
                buildQueueEntry("CUST-002", 60, 8)
        );

        when(queueRepository.findByStatusOrderByPriorityDesc("PENDING")).thenReturn(pendingAccounts);
        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"), anyString(), anyMap()))
                .thenReturn(allowDecision());
        // No prior contacts this week
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        scheduler.runDailyCollectionsBatch();

        // Both accounts should be processed
        verify(interactionRepository, times(2)).save(any());
        verify(queueRepository, times(2)).save(argThat(q -> "IN_PROGRESS".equals(q.getStatus())));
        verify(auditEventPublisher, times(2)).publish(any());
    }

    @Test
    @DisplayName("Should skip accounts when vault denies")
    void shouldSkipAccountsWhenVaultDenies() {
        List<CollectionsQueue> pendingAccounts = List.of(
                buildQueueEntry("CUST-001", 30, 5)
        );

        when(queueRepository.findByStatusOrderByPriorityDesc("PENDING")).thenReturn(pendingAccounts);
        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"), anyString(), anyMap()))
                .thenReturn(denyDecision());

        scheduler.runDailyCollectionsBatch();

        // No interaction recorded, no status update
        verify(interactionRepository, never()).save(any());
        verify(queueRepository, never()).save(any());
        // Audit event still published for the denial
        verify(auditEventPublisher).publish(any());
    }

    @Test
    @DisplayName("Should skip accounts when weekly contact limit is reached")
    void shouldSkipAccountsWhenContactLimitReached() {
        List<CollectionsQueue> pendingAccounts = List.of(
                buildQueueEntry("CUST-001", 30, 5)
        );

        when(queueRepository.findByStatusOrderByPriorityDesc("PENDING")).thenReturn(pendingAccounts);
        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"), anyString(), anyMap()))
                .thenReturn(allowDecision());
        // Already contacted 3 times this week
        when(valueOperations.get(anyString())).thenReturn("3");

        scheduler.runDailyCollectionsBatch();

        // No interaction recorded, no status update
        verify(interactionRepository, never()).save(any());
        verify(queueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process some and skip others based on contact count")
    void shouldProcessSomeAndSkipOthers() {
        List<CollectionsQueue> pendingAccounts = List.of(
                buildQueueEntry("CUST-001", 30, 8),  // Will be allowed
                buildQueueEntry("CUST-002", 45, 5)   // Will hit contact limit
        );

        when(queueRepository.findByStatusOrderByPriorityDesc("PENDING")).thenReturn(pendingAccounts);
        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"), anyString(), anyMap()))
                .thenReturn(allowDecision());

        // CUST-001: no prior contacts
        when(valueOperations.get(argThat(key -> key != null && key.contains("CUST-001")))).thenReturn("0");
        when(valueOperations.increment(argThat(key -> key != null && key.contains("CUST-001")))).thenReturn(1L);

        // CUST-002: already at limit
        when(valueOperations.get(argThat(key -> key != null && key.contains("CUST-002")))).thenReturn("3");

        scheduler.runDailyCollectionsBatch();

        // Only CUST-001 should be processed
        verify(interactionRepository, times(1)).save(any());
        verify(queueRepository, times(1)).save(argThat(q -> "IN_PROGRESS".equals(q.getStatus())));
    }

    @Test
    @DisplayName("Should increment contact count with TTL on first contact")
    void shouldIncrementContactCountWithTtl() {
        CollectionsQueue account = buildQueueEntry("CUST-001", 30, 5);

        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"), anyString(), anyMap()))
                .thenReturn(allowDecision());
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        scheduler.processAccount(account);

        verify(valueOperations).increment(argThat(key -> key.startsWith("collections:contact_count:CUST-001:")));
        verify(redisTemplate).expire(
                argThat(key -> ((String) key).startsWith("collections:contact_count:CUST-001:")),
                any());
    }

    @Test
    @DisplayName("Should handle empty queue gracefully")
    void shouldHandleEmptyQueue() {
        when(queueRepository.findByStatusOrderByPriorityDesc("PENDING")).thenReturn(List.of());

        scheduler.runDailyCollectionsBatch();

        verify(vaultClient, never()).evaluatePolicy(anyString(), anyString(), anyString(), anyMap());
        verify(interactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle vault escalate decision")
    void shouldHandleVaultEscalateDecision() {
        List<CollectionsQueue> pendingAccounts = List.of(
                buildQueueEntry("CUST-001", 30, 5)
        );

        PolicyDecision escalate = new PolicyDecision(
                PolicyDecision.Decision.ESCALATE, "Requires supervisor approval", "POLICY-003");

        when(queueRepository.findByStatusOrderByPriorityDesc("PENDING")).thenReturn(pendingAccounts);
        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"), anyString(), anyMap()))
                .thenReturn(escalate);

        scheduler.runDailyCollectionsBatch();

        // Should not process (ESCALATE != ALLOW)
        verify(interactionRepository, never()).save(any());
        verify(queueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should continue processing batch even if one account fails")
    void shouldContinueBatchOnIndividualFailure() {
        CollectionsQueue account1 = buildQueueEntry("CUST-001", 30, 8);
        CollectionsQueue account2 = buildQueueEntry("CUST-002", 45, 5);

        when(queueRepository.findByStatusOrderByPriorityDesc("PENDING"))
                .thenReturn(List.of(account1, account2));

        // First call throws exception, second succeeds
        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"),
                eq("customer:CUST-001"), anyMap()))
                .thenThrow(new RuntimeException("Vault timeout"));
        when(vaultClient.evaluatePolicy(anyString(), eq("collections_call"),
                eq("customer:CUST-002"), anyMap()))
                .thenReturn(allowDecision());
        when(valueOperations.get(argThat(key -> key != null && key.contains("CUST-002")))).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        scheduler.runDailyCollectionsBatch();

        // CUST-002 should still be processed despite CUST-001 failure
        verify(interactionRepository, times(1)).save(any());
    }
}
