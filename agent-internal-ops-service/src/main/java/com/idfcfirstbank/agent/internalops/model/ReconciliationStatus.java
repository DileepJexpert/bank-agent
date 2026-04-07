package com.idfcfirstbank.agent.internalops.model;

import java.time.Instant;

public record ReconciliationStatus(
        String reconciliationId,
        Instant lastRunTime,
        int matchedCount,
        int mismatchCount,
        int pendingCount,
        String status,
        String summary
) {
}
