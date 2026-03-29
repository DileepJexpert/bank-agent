package com.idfcfirstbank.agent.vault.audit.service;

import com.idfcfirstbank.agent.vault.audit.model.AuditEventEntity;
import com.idfcfirstbank.agent.vault.audit.model.dto.AuditExportRequest;
import com.idfcfirstbank.agent.vault.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for asynchronous export of audit events in regulatory formats.
 * Supports RBI, SEBI, and FIU_IND export formats as required by Indian banking regulations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditExportService {

    private final AuditEventRepository auditEventRepository;

    /**
     * In-memory job tracker. In production, this would be backed by Redis or a database table.
     */
    private final Map<String, ExportJobStatus> jobTracker = new ConcurrentHashMap<>();

    /**
     * Triggers an asynchronous audit export job.
     *
     * @param request the export parameters including format, date range, and optional agent filter
     * @return the generated job ID for tracking export progress
     */
    public String triggerExport(AuditExportRequest request) {
        String jobId = UUID.randomUUID().toString();
        jobTracker.put(jobId, new ExportJobStatus("QUEUED", 0, null));

        log.info("Export job created: jobId={}, format={}, dateRange=[{}, {}]",
                jobId, request.format(), request.dateFrom(), request.dateTo());

        executeExportAsync(jobId, request);
        return jobId;
    }

    /**
     * Returns the status of an export job.
     *
     * @param jobId the job identifier
     * @return the job status, or null if not found
     */
    public ExportJobStatus getJobStatus(String jobId) {
        return jobTracker.get(jobId);
    }

    @Async
    protected void executeExportAsync(String jobId, AuditExportRequest request) {
        try {
            jobTracker.put(jobId, new ExportJobStatus("PROCESSING", 0, null));
            log.info("Starting export job: jobId={}, format={}", jobId, request.format());

            // Fetch events in batches for memory efficiency
            int pageSize = 1000;
            int pageNumber = 0;
            long totalExported = 0;

            Page<AuditEventEntity> page;
            do {
                page = auditEventRepository.findFiltered(
                        request.agentId(), null, null,
                        request.dateFrom(), request.dateTo(),
                        PageRequest.of(pageNumber, pageSize));

                for (AuditEventEntity event : page.getContent()) {
                    formatEvent(event, request.format());
                    totalExported++;
                }

                pageNumber++;
                jobTracker.put(jobId, new ExportJobStatus("PROCESSING", totalExported, null));

            } while (page.hasNext());

            jobTracker.put(jobId, new ExportJobStatus("COMPLETED", totalExported, null));
            log.info("Export job completed: jobId={}, format={}, totalEvents={}",
                    jobId, request.format(), totalExported);

        } catch (Exception e) {
            log.error("Export job failed: jobId={}, error={}", jobId, e.getMessage(), e);
            jobTracker.put(jobId, new ExportJobStatus("FAILED", 0, e.getMessage()));
        }
    }

    /**
     * Formats a single audit event according to the specified regulatory format.
     * In production, this would write to a file or streaming output.
     */
    private void formatEvent(AuditEventEntity event, String format) {
        switch (format) {
            case "RBI" -> formatForRbi(event);
            case "SEBI" -> formatForSebi(event);
            case "FIU_IND" -> formatForFiuInd(event);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        }
    }

    private void formatForRbi(AuditEventEntity event) {
        // RBI format: includes all fields with RBI-mandated column ordering
        // In production, this writes to a CSV/XML file in the prescribed RBI format
        log.trace("Formatting event {} for RBI export", event.getEventId());
    }

    private void formatForSebi(AuditEventEntity event) {
        // SEBI format: focused on investment/wealth-related audit events
        // In production, this writes to the SEBI-prescribed reporting format
        log.trace("Formatting event {} for SEBI export", event.getEventId());
    }

    private void formatForFiuInd(AuditEventEntity event) {
        // FIU-IND format: Financial Intelligence Unit reporting for suspicious transactions
        // In production, this writes to the FIU-IND STR/CTR format
        log.trace("Formatting event {} for FIU-IND export", event.getEventId());
    }

    /**
     * Represents the status of an export job.
     */
    public record ExportJobStatus(String status, long eventsProcessed, String error) {
    }
}
