package com.idfcfirstbank.agent.vault.audit.controller;

import com.idfcfirstbank.agent.vault.audit.model.AuditEventEntity;
import com.idfcfirstbank.agent.vault.audit.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit", description = "Audit event query and statistics endpoints")
public class AuditController {

    private final AuditQueryService auditQueryService;

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
}
