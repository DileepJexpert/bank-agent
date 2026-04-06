package com.idfcfirstbank.agent.internalops.model;

import java.util.Map;

public record InternalOpsRequest(
        String sessionId,
        String employeeId,
        String message,
        String intent,
        double confidence,
        Map<String, Object> parameters
) {
}
