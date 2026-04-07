package com.idfcfirstbank.agent.internalops.model;

public record InternalOpsResponse(
        String sessionId,
        String message,
        String intent,
        boolean escalated,
        String reportId
) {
}
