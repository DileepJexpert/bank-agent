package com.idfcfirstbank.agent.vault.anomaly.controller;

import com.idfcfirstbank.agent.vault.anomaly.model.AgentBaseline;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.AnomalyType;
import com.idfcfirstbank.agent.vault.anomaly.model.AnomalyEvent.Severity;
import com.idfcfirstbank.agent.vault.anomaly.repository.AnomalyEventRepository;
import com.idfcfirstbank.agent.vault.anomaly.service.BaselineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for querying and managing anomaly detection events.
 */
@RestController
@RequestMapping("/api/v1/anomaly")
@RequiredArgsConstructor
@Slf4j
public class AnomalyController {

    private final AnomalyEventRepository anomalyEventRepository;
    private final BaselineService baselineService;

    /**
     * Returns a paginated list of anomaly events with optional filters.
     */
    @GetMapping("/events")
    public ResponseEntity<Page<AnomalyEvent>> getAnomalyEvents(
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) AnomalyType anomalyType,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<AnomalyEvent> events = anomalyEventRepository.findWithFilters(
                severity, anomalyType, agentId, from, to, pageable);
        return ResponseEntity.ok(events);
    }

    /**
     * Returns a specific anomaly event by its alert ID.
     */
    @GetMapping("/events/{alertId}")
    public ResponseEntity<AnomalyEvent> getAnomalyEvent(@PathVariable UUID alertId) {
        return anomalyEventRepository.findById(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns current agent behavioral baselines.
     */
    @GetMapping("/baselines")
    public ResponseEntity<Map<String, AgentBaseline>> getBaselines() {
        return ResponseEntity.ok(baselineService.getAllBaselines());
    }

    /**
     * Marks an anomaly event as resolved with optional resolution notes.
     */
    @PostMapping("/events/{alertId}/resolve")
    public ResponseEntity<AnomalyEvent> resolveAnomalyEvent(
            @PathVariable UUID alertId,
            @RequestBody @Valid ResolveRequest request) {

        return anomalyEventRepository.findById(alertId)
                .map(event -> {
                    if (event.getResolvedAt() != null) {
                        log.warn("Anomaly event already resolved: alertId={}", alertId);
                        return ResponseEntity.ok(event);
                    }
                    event.setResolvedAt(Instant.now());
                    event.setResolutionNotes(request.resolutionNotes());
                    AnomalyEvent saved = anomalyEventRepository.save(event);
                    log.info("Anomaly event resolved: alertId={}, notes={}", alertId, request.resolutionNotes());
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Request body for resolving an anomaly event.
     */
    public record ResolveRequest(
            @NotBlank(message = "Resolution notes are required")
            String resolutionNotes
    ) {}
}
