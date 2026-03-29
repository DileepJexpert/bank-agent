package com.idfcfirstbank.agent.vault.audit.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Request DTO for triggering an asynchronous audit export in a regulatory format.
 *
 * @param format    the regulatory export format (RBI, SEBI, FIU_IND)
 * @param dateFrom  start of the export date range (inclusive)
 * @param dateTo    end of the export date range (inclusive)
 * @param agentId   optional filter by agent ID
 */
public record AuditExportRequest(
        @NotBlank(message = "Export format is required (RBI, SEBI, FIU_IND)")
        String format,

        @NotNull(message = "Start date is required")
        Instant dateFrom,

        @NotNull(message = "End date is required")
        Instant dateTo,

        String agentId
) {
}
