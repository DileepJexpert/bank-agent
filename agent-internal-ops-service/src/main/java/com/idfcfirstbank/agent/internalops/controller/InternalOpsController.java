package com.idfcfirstbank.agent.internalops.controller;

import com.idfcfirstbank.agent.internalops.model.InternalOpsRequest;
import com.idfcfirstbank.agent.internalops.model.InternalOpsResponse;
import com.idfcfirstbank.agent.internalops.model.MisReport;
import com.idfcfirstbank.agent.internalops.model.ReconciliationStatus;
import com.idfcfirstbank.agent.internalops.service.InternalOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal-ops")
@RequiredArgsConstructor
@Tag(name = "Internal Ops Agent", description = "Employee-facing internal operations")
public class InternalOpsController {

    private final InternalOpsService internalOpsService;

    @PostMapping("/process")
    @Operation(summary = "Process internal ops query", description = "AI-powered internal operations query processing")
    public ResponseEntity<InternalOpsResponse> process(@Valid @RequestBody InternalOpsRequest request) {
        log.info("Internal ops query: employeeId={}, intent={}, sessionId={}",
                request.employeeId(), request.intent(), request.sessionId());

        InternalOpsResponse response = internalOpsService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mis-report")
    @Operation(summary = "Generate MIS report", description = "Generate daily transaction summary for a branch")
    public ResponseEntity<MisReport> generateMisReport(@RequestBody Map<String, String> body) {
        String branchId = body.get("branchId");
        String date = body.getOrDefault("date", "");

        log.info("MIS report request: branchId={}, date={}", branchId, date);

        MisReport report = internalOpsService.generateMisReport(branchId, date);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Get reconciliation status", description = "Today's reconciliation status")
    public ResponseEntity<ReconciliationStatus> getReconciliationStatus() {
        log.info("Reconciliation status request");
        ReconciliationStatus status = internalOpsService.getReconciliationStatus();
        return ResponseEntity.ok(status);
    }

    @PostMapping("/it-helpdesk")
    @Operation(summary = "IT helpdesk query", description = "Query IT knowledge base for help")
    public ResponseEntity<InternalOpsResponse> itHelpdesk(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        String employeeId = body.getOrDefault("employeeId", "");

        log.info("IT helpdesk query: employeeId={}", employeeId);

        InternalOpsResponse response = internalOpsService.handleItHelpdesk(employeeId, query);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/hr-query")
    @Operation(summary = "HR query", description = "Employee HR queries (leave balance, policies)")
    public ResponseEntity<InternalOpsResponse> hrQuery(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        String employeeId = body.getOrDefault("employeeId", "");

        log.info("HR query: employeeId={}", employeeId);

        InternalOpsResponse response = internalOpsService.handleHrQuery(employeeId, query);
        return ResponseEntity.ok(response);
    }
}
