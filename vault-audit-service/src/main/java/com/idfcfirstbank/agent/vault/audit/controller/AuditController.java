package com.idfcfirstbank.agent.vault.audit.controller;

import com.idfcfirstbank.agent.vault.audit.model.AuditEventEntity;
import com.idfcfirstbank.agent.vault.audit.model.dto.AuditExportRequest;
import com.idfcfirstbank.agent.vault.audit.service.AuditExportService;
import com.idfcfirstbank.agent.vault.audit.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit", description = "Audit event query and statistics endpoints")
public class AuditController {

    private static final Set<String> VALID_EXPORT_FORMATS = Set.of("RBI", "SEBI", "FIU_IND");

    private final AuditQueryService auditQueryService;
    private final AuditExportService auditExportService;

    @GetMapping("/events")
    @Operation(summary = "Query audit events",
            description = "Returns paginated audit events with optional filters for agent, action, result, and time range")
    public ResponseEntity<Page<AuditEventEntity>> queryEvents(
            @Parameter(description = "Filter by agent ID")
            @RequestParam(required = false) String agentId,
            @Parameter(description = "Filter by action")
            @RequestParam(required = false) String action,
            @Parameter(description = "Filter by policy result (ALLOW, DENY, ESCALATE)")
            @RequestParam(required = false) String policyResult,
            @Parameter(description = "Filter events from this time (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Filter events to this time (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<AuditEventEntity> events = auditQueryService.queryEvents(
                agentId, action, policyResult, from, to, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get specific audit event",
            description = "Returns a single audit event by its event ID")
    public ResponseEntity<AuditEventEntity> getEvent(
            @PathVariable String eventId) {
        return auditQueryService.getByEventId(eventId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/agents/{agentId}/events")
    @Operation(summary = "Get events by agent",
            description = "Returns paginated audit events for a specific agent")
    public ResponseEntity<Page<AuditEventEntity>> getEventsByAgent(
            @PathVariable String agentId,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<AuditEventEntity> events = auditQueryService.getEventsByAgent(agentId, pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get audit statistics",
            description = "Returns aggregated audit statistics including decision counts and agent activity")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @Parameter(description = "Statistics window in hours (default 24)")
            @RequestParam(defaultValue = "24") int hours) {

        Map<String, Object> stats = auditQueryService.getStatistics(hours);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/events/correlation/{correlationId}")
    @Operation(summary = "Trace events by correlation ID",
            description = "Returns all audit events sharing a correlation ID, ordered chronologically. "
                    + "Use this to trace a full customer interaction across multiple agents.")
    public ResponseEntity<List<AuditEventEntity>> getEventsByCorrelation(
            @PathVariable UUID correlationId) {
        log.info("Correlation trace requested: correlationId={}", correlationId);
        List<AuditEventEntity> events = auditQueryService.getEventsByCorrelationId(correlationId);
        if (events.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(events);
    }

    @PostMapping("/export")
    @Operation(summary = "Export audit events in regulatory format",
            description = "Triggers an asynchronous export of audit events in the specified regulatory format "
                    + "(RBI, SEBI, or FIU_IND). Returns a job ID for tracking export progress.")
    public ResponseEntity<Map<String, Object>> exportAuditEvents(
            @Valid @RequestBody AuditExportRequest request) {

        if (!VALID_EXPORT_FORMATS.contains(request.format())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid export format",
                    "validFormats", VALID_EXPORT_FORMATS
            ));
        }

        if (request.dateFrom().isAfter(request.dateTo())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "dateFrom must be before dateTo"
            ));
        }

        log.info("Audit export requested: format={}, from={}, to={}, agentId={}",
                request.format(), request.dateFrom(), request.dateTo(), request.agentId());

        String jobId = auditExportService.triggerExport(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", "ACCEPTED",
                "format", request.format(),
                "message", "Export job has been queued. Use the jobId to check progress."
        ));
    }
}
