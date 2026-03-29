package com.idfcfirstbank.agent.vault.policy.controller;

import com.idfcfirstbank.agent.vault.policy.model.PolicyEvaluationRequest;
import com.idfcfirstbank.agent.vault.policy.model.PolicyEvaluationResponse;
import com.idfcfirstbank.agent.vault.policy.service.PolicyEvaluationService;
import com.idfcfirstbank.agent.vault.policy.service.PolicyReloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/policy")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Policy", description = "OPA policy evaluation and management endpoints")
public class PolicyController {

    private final PolicyEvaluationService evaluationService;
    private final PolicyReloadService reloadService;

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate a policy request",
            description = "Evaluates agent action against OPA policies and returns ALLOW/DENY/ESCALATE decision")
    public ResponseEntity<PolicyEvaluationResponse> evaluate(
            @Valid @RequestBody PolicyEvaluationRequest request) {
        log.info("Policy evaluation request: agent={}, action={}, resource={}",
                request.agentId(), request.action(), request.resource());
        PolicyEvaluationResponse response = evaluationService.evaluate(request);
        log.info("Policy decision for agent={}: {} ({}ms)",
                request.agentId(), response.decision(), response.evaluationTimeMs());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reload")
    @Operation(summary = "Hot-reload policies from Git",
            description = "Reloads Rego policies from the configured Git repository path without restart")
    public ResponseEntity<Map<String, String>> reloadPolicies() {
        log.info("Policy reload requested");
        reloadService.reloadPolicies();
        return ResponseEntity.ok(Map.of("status", "reloaded", "message", "Policies reloaded successfully"));
    }

    @GetMapping("/list")
    @Operation(summary = "List active policies",
            description = "Returns a list of all currently loaded and active policy identifiers")
    public ResponseEntity<List<String>> listPolicies() {
        List<String> policies = reloadService.listActivePolicies();
        return ResponseEntity.ok(policies);
    }
}
