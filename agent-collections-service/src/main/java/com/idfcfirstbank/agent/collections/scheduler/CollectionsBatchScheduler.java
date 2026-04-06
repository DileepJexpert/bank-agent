package com.idfcfirstbank.agent.collections.scheduler;

import com.idfcfirstbank.agent.collections.entity.CollectionsInteraction;
import com.idfcfirstbank.agent.collections.entity.CollectionsQueue;
import com.idfcfirstbank.agent.collections.repository.CollectionsInteractionRepository;
import com.idfcfirstbank.agent.collections.repository.CollectionsQueueRepository;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled batch processor for outbound collections calls.
 * <p>
 * Runs Monday-Saturday at 9:00 AM, fetches all PENDING overdue accounts from the
 * collections queue, and for each account:
 * <ol>
 *   <li>Checks vault policy (action=COLLECTIONS_CALL) with current hour in context</li>
 *   <li>Checks Redis weekly contact count - skips if &gt;= 3 contacts this week (RBI compliance)</li>
 *   <li>If ALLOW: logs the outbound call attempt and records the interaction</li>
 *   <li>First message is always the mandatory RBI disclosure</li>
 * </ol>
 * <p>
 * Contact counts are tracked in Redis with a 7-day TTL for automatic expiry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionsBatchScheduler {

    private static final String AGENT_ID = "collections-agent";
    private static final String CONTACT_COUNT_KEY_PREFIX = "collections:contact_count:";
    private static final Duration CONTACT_COUNT_TTL = Duration.ofDays(7);
    private static final int MAX_WEEKLY_CONTACTS = 3;
    private static final String RBI_DISCLOSURE_MESSAGE =
            "This is IDFC First Bank AI assistant. This call is recorded per RBI guidelines.";

    private final CollectionsQueueRepository queueRepository;
    private final CollectionsInteractionRepository interactionRepository;
    private final VaultClient vaultClient;
    private final AuditEventPublisher auditEventPublisher;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${spring.application.name:agent-collections-service}")
    private String instanceId;

    /**
     * Run the daily collections batch at 9:00 AM, Monday through Saturday.
     * Fetches PENDING accounts, applies policy and contact-frequency checks,
     * and initiates outbound collection calls for eligible accounts.
     */
    @Scheduled(cron = "0 0 9 * * MON-SAT")
    public void runDailyCollectionsBatch() {
        log.info("Starting daily collections batch at {}", Instant.now());
        long batchStartTime = System.currentTimeMillis();

        List<CollectionsQueue> pendingAccounts = queueRepository.findByStatusOrderByPriorityDesc("PENDING");
        log.info("Found {} pending accounts for collection", pendingAccounts.size());

        int processed = 0;
        int skippedPolicy = 0;
        int skippedContactLimit = 0;

        for (CollectionsQueue account : pendingAccounts) {
            try {
                boolean result = processAccount(account);
                if (result) {
                    processed++;
                } else {
                    // Determine why it was skipped for logging
                    int contactCount = getContactCount(account.getCustomerId());
                    if (contactCount >= MAX_WEEKLY_CONTACTS) {
                        skippedContactLimit++;
                    } else {
                        skippedPolicy++;
                    }
                }
            } catch (Exception e) {
                log.error("Error processing account: customerId={}, queueId={}",
                        account.getCustomerId(), account.getQueueId(), e);
            }
        }

        long batchDuration = System.currentTimeMillis() - batchStartTime;
        log.info("Collections batch completed: total={}, processed={}, skippedPolicy={}, "
                        + "skippedContactLimit={}, durationMs={}",
                pendingAccounts.size(), processed, skippedPolicy, skippedContactLimit, batchDuration);
    }

    /**
     * Process a single account from the collections queue.
     *
     * @return true if the outbound call was initiated, false if skipped
     */
    boolean processAccount(CollectionsQueue account) {
        String customerId = account.getCustomerId();
        long startTime = System.currentTimeMillis();

        // Step 1: Check vault policy with current hour context
        int currentHour = LocalTime.now().getHour();
        Map<String, Object> policyContext = new HashMap<>();
        policyContext.put("currentHour", String.valueOf(currentHour));
        policyContext.put("daysOverdue", String.valueOf(account.getDaysOverdue()));
        policyContext.put("overdueAmount", account.getOverdueAmount().toPlainString());
        policyContext.put("productType", account.getProductType());
        policyContext.put("channel", "outbound_batch");

        PolicyDecision decision = vaultClient.evaluatePolicy(
                AGENT_ID,
                "collections_call",
                "customer:" + customerId,
                policyContext
        );

        if (decision.decision() != PolicyDecision.Decision.ALLOW) {
            log.info("Vault policy {} for outbound call: customerId={}, reason={}",
                    decision.decision(), customerId, decision.reason());
            publishAudit(customerId, "COLLECTIONS_CALL", decision, "Skipped by policy", startTime);
            return false;
        }

        // Step 2: Check Redis contact count - max 3 per week per RBI guidelines
        int contactCount = getContactCount(customerId);
        if (contactCount >= MAX_WEEKLY_CONTACTS) {
            log.info("Weekly contact limit reached for customerId={}: count={}/{}",
                    customerId, contactCount, MAX_WEEKLY_CONTACTS);
            publishAudit(customerId, "COLLECTIONS_CALL", decision,
                    "Skipped: weekly contact limit reached (" + contactCount + "/" + MAX_WEEKLY_CONTACTS + ")",
                    startTime);
            return false;
        }

        // Step 3: Initiate outbound call
        log.info("Initiating outbound collection call: customerId={}, overdueAmount={}, daysOverdue={}, priority={}",
                customerId, account.getOverdueAmount(), account.getDaysOverdue(), account.getPriority());

        // First message MUST be the RBI disclosure
        String transcript = RBI_DISCLOSURE_MESSAGE + "\n\n"
                + String.format("Dear Customer, this call is regarding your overdue amount of INR %s "
                        + "on your %s account. The amount has been overdue for %d days. "
                        + "We would like to discuss available resolution options with you.",
                account.getOverdueAmount().toPlainString(),
                account.getProductType(),
                account.getDaysOverdue());

        // Record the interaction
        CollectionsInteraction interaction = CollectionsInteraction.builder()
                .customerId(customerId)
                .callTimestamp(Instant.now())
                .outcome("OUTBOUND_CALL_INITIATED")
                .offerMade("Collections outbound call")
                .transcript(transcript)
                .build();
        interactionRepository.save(interaction);

        // Update queue status
        account.setStatus("IN_PROGRESS");
        queueRepository.save(account);

        // Increment contact count in Redis with 7-day TTL
        incrementContactCount(customerId);

        publishAudit(customerId, "COLLECTIONS_CALL", decision,
                "Outbound call initiated", startTime);

        return true;
    }

    // ── Redis contact tracking ──

    int getContactCount(String customerId) {
        try {
            String yearWeek = getYearWeek();
            String key = CONTACT_COUNT_KEY_PREFIX + customerId + ":" + yearWeek;
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("Failed to read contact count from Redis for customerId={}: {}",
                    customerId, e.getMessage());
            return 0;
        }
    }

    private void incrementContactCount(String customerId) {
        try {
            String yearWeek = getYearWeek();
            String key = CONTACT_COUNT_KEY_PREFIX + customerId + ":" + yearWeek;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, CONTACT_COUNT_TTL);
            }
            log.debug("Contact count incremented for customerId={}, week={}: {}", customerId, yearWeek, count);
        } catch (Exception e) {
            log.warn("Failed to increment contact count in Redis for customerId={}: {}",
                    customerId, e.getMessage());
        }
    }

    private String getYearWeek() {
        LocalDate now = LocalDate.now();
        int weekOfYear = now.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
        return now.getYear() + "-W" + String.format("%02d", weekOfYear);
    }

    // ── Audit ──

    private void publishAudit(String customerId, String action,
                              PolicyDecision decision, String responsePayload, long startTime) {
        try {
            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    AGENT_ID,
                    instanceId,
                    customerId,
                    action,
                    "customer:" + customerId,
                    decision.decision().name(),
                    "batch_scheduler",
                    responsePayload != null ? responsePayload : "",
                    System.currentTimeMillis() - startTime
            );
            auditEventPublisher.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish audit event for customerId={}, action={}: {}",
                    customerId, action, e.getMessage());
        }
    }
}
