package com.idfcfirstbank.agent.vault.policy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class PolicyReloadService {

    private final List<String> activePolicies = new CopyOnWriteArrayList<>();
    private final RestClient opaClient;

    @Value("${vault.policy.git-path:classpath:policies}")
    private String policyGitPath;

    @Value("${vault.policy.file-path:src/main/resources/policies}")
    private String policyFilePath;

    public PolicyReloadService(@Value("${vault.opa.url:http://localhost:8181}") String opaUrl) {
        this.opaClient = RestClient.builder().baseUrl(opaUrl).build();
    }

    @PostConstruct
    public void init() {
        log.info("Initializing policy reload service, loading policies from: {}", policyFilePath);
        reloadPolicies();
    }

    /**
     * Reloads Rego policies from the configured Git/file path.
     * Validates policies before activation and pushes them to OPA.
     */
    public void reloadPolicies() {
        Path policiesDir = Paths.get(policyFilePath);
        if (!Files.exists(policiesDir)) {
            log.warn("Policies directory not found: {}. Using defaults.", policyFilePath);
            activePolicies.clear();
            activePolicies.add("default-policies.rego");
            return;
        }

        List<String> loaded = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(policiesDir, "*.rego")) {
            for (Path policyFile : stream) {
                String policyName = policyFile.getFileName().toString();
                try {
                    String content = Files.readString(policyFile);

                    if (!validatePolicy(content)) {
                        log.error("Policy validation failed for: {}. Skipping.", policyName);
                        continue;
                    }

                    pushPolicyToOpa(policyName, content);
                    loaded.add(policyName);
                    log.info("Loaded policy: {}", policyName);
                } catch (IOException e) {
                    log.error("Failed to read policy file {}: {}", policyName, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan policies directory: {}", e.getMessage());
        }

        activePolicies.clear();
        activePolicies.addAll(loaded);
        log.info("Policy reload complete. Active policies: {}", activePolicies);
    }

    /**
     * Returns the list of currently active policy identifiers.
     */
    public List<String> listActivePolicies() {
        return Collections.unmodifiableList(activePolicies);
    }

    /**
     * Validates a Rego policy by checking basic syntax constraints.
     * In production, this would call OPA's compile endpoint for full validation.
     */
    private boolean validatePolicy(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        // Basic validation: ensure it contains a package declaration
        if (!content.contains("package ")) {
            log.warn("Policy missing package declaration");
            return false;
        }
        return true;
    }

    /**
     * Pushes a Rego policy to OPA via its Policy API.
     */
    private void pushPolicyToOpa(String policyName, String content) {
        try {
            String policyId = policyName.replace(".rego", "").replace("-", "_");
            opaClient.put()
                    .uri("/v1/policies/{id}", policyId)
                    .header("Content-Type", "text/plain")
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Pushed policy to OPA: {}", policyId);
        } catch (Exception e) {
            log.warn("Failed to push policy {} to OPA (OPA may not be running): {}",
                    policyName, e.getMessage());
        }
    }
}
