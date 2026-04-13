package com.idfcfirstbank.agent.internalops.service;

import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.vault.PolicyDecision;
import com.idfcfirstbank.agent.common.vault.VaultClient;
import com.idfcfirstbank.agent.internalops.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.idfcfirstbank.agent.common.llm.LlmRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalOpsService {

    private static final String SYSTEM_PROMPT = """
            You are the Internal Operations Agent for IDFC First Bank. You serve bank employees
            (not customers). You handle:
            - MIS Report generation (daily transaction summaries by branch)
            - Reconciliation status queries
            - Compliance queries (KYC renewals, regulatory checks)
            - IT Helpdesk queries (password resets, system access)
            - HR queries (leave balance, policies)

            Always maintain a professional tone suitable for internal communications.
            Provide accurate, structured data in your responses.
            Flag any compliance concerns immediately.
            """;

    private final LlmRouter llmRouter;
    private final VaultClient vaultClient;
    private final AuditEventPublisher auditEventPublisher;
    private final RestTemplate restTemplate;

    @Value("${agent.mcp.core-banking-url:http://mcp-core-banking-server:8086}")
    private String coreBankingUrl;

    @Value("${agent.mcp.crm-url:http://mcp-crm-server:8098}")
    private String crmUrl;

    public InternalOpsResponse processQuery(InternalOpsRequest request) {
        long startTime = System.currentTimeMillis();

        PolicyDecision decision = vaultClient.evaluatePolicy(
                "internal-ops-agent",
                request.intent() != null ? request.intent() : "internal_query",
                "employee:" + request.employeeId(),
                Map.of("sessionId", request.sessionId(), "channel", "internal")
        );

        if (decision.decision() == PolicyDecision.Decision.DENY) {
            log.warn("Vault policy denied internal ops query: employeeId={}, intent={}, reason={}",
                    request.employeeId(), request.intent(), decision.reason());
            return new InternalOpsResponse(
                    request.sessionId(),
                    "Access denied. " + decision.reason(),
                    request.intent(),
                    false,
                    null
            );
        }

        if (decision.decision() == PolicyDecision.Decision.ESCALATE) {
            return new InternalOpsResponse(
                    request.sessionId(),
                    "This request requires manager approval. Escalating to your reporting manager.",
                    request.intent(),
                    true,
                    null
            );
        }

        try {
            InternalOpsResponse response = switch (request.intent() != null ? request.intent().toUpperCase() : "") {
                case "MIS_REPORT" -> handleMisReport(request);
                case "RECONCILIATION_STATUS" -> handleReconciliation(request);
                case "COMPLIANCE_QUERY" -> handleComplianceQuery(request);
                case "IT_HELPDESK" -> handleItHelpdesk(request.employeeId(), request.message());
                case "HR_QUERY" -> handleHrQuery(request.employeeId(), request.message());
                default -> handleGeneralQuery(request);
            };

            publishAudit(request.employeeId(), request.intent(), "ALLOW", startTime);
            return response;

        } catch (Exception e) {
            log.error("Error processing internal ops query: employeeId={}, intent={}",
                    request.employeeId(), request.intent(), e);
            return new InternalOpsResponse(
                    request.sessionId(),
                    "An error occurred processing your request. Please try again or contact IT support.",
                    request.intent(),
                    false,
                    null
            );
        }
    }

    private InternalOpsResponse handleMisReport(InternalOpsRequest request) {
        String branchId = request.parameters() != null
                ? (String) request.parameters().getOrDefault("branchId", "")
                : "";
        String date = request.parameters() != null
                ? (String) request.parameters().getOrDefault("date", "")
                : "";

        MisReport report = generateMisReport(branchId, date);

        String message = String.format("""
                MIS Report Generated Successfully
                ---------------------------------
                Branch: %s (%s)
                Date: %s
                Total Transactions: %d
                Total Credit: INR %.2f
                Total Debit: INR %.2f
                Breakdown by Type: %s
                Report ID: %s
                """,
                report.branchName(), report.branchId(),
                report.reportDate(), report.totalTransactions(),
                report.totalCredit(), report.totalDebit(),
                report.transactionsByType(), report.reportId());

        return new InternalOpsResponse(request.sessionId(), message, "MIS_REPORT", false, report.reportId());
    }

    public MisReport generateMisReport(String branchId, String date) {
        LocalDate reportDate = (date != null && !date.isEmpty())
                ? LocalDate.parse(date, DateTimeFormatter.ISO_DATE)
                : LocalDate.now();

        try {
            String url = coreBankingUrl + "/api/v1/core-banking/branch-transactions";
            Map<String, String> body = Map.of("branchId", branchId, "date", reportDate.toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

            if (response != null) {
                String reportId = "MIS-" + branchId + "-" + reportDate.format(DateTimeFormatter.BASIC_ISO_DATE);
                int totalTxns = (int) response.getOrDefault("totalTransactions", 0);
                double totalCredit = ((Number) response.getOrDefault("totalCredit", 0.0)).doubleValue();
                double totalDebit = ((Number) response.getOrDefault("totalDebit", 0.0)).doubleValue();

                @SuppressWarnings("unchecked")
                Map<String, Integer> byType = (Map<String, Integer>) response.getOrDefault("byType", Map.of());

                return new MisReport(reportId, branchId,
                        (String) response.getOrDefault("branchName", branchId),
                        reportDate, totalTxns, totalCredit, totalDebit, byType, "COMPLETED");
            }
        } catch (Exception e) {
            log.error("Failed to fetch branch transactions from MCP: {}", e.getMessage());
        }

        // Return mock data when MCP is unavailable
        String reportId = "MIS-" + branchId + "-" + reportDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return new MisReport(reportId, branchId, branchId, reportDate,
                0, 0.0, 0.0, Map.of(), "MCP_UNAVAILABLE");
    }

    private InternalOpsResponse handleReconciliation(InternalOpsRequest request) {
        ReconciliationStatus status = getReconciliationStatus();

        String message = String.format("""
                Reconciliation Status
                --------------------
                Last Run: %s
                Matched: %d
                Mismatches: %d
                Pending: %d
                Status: %s
                Summary: %s
                """,
                status.lastRunTime(), status.matchedCount(),
                status.mismatchCount(), status.pendingCount(),
                status.status(), status.summary());

        return new InternalOpsResponse(request.sessionId(), message, "RECONCILIATION_STATUS", false, status.reconciliationId());
    }

    public ReconciliationStatus getReconciliationStatus() {
        try {
            String url = coreBankingUrl + "/api/v1/core-banking/reconciliation/status";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null) {
                return new ReconciliationStatus(
                        (String) response.getOrDefault("reconciliationId", "RECON-" + LocalDate.now()),
                        Instant.now(),
                        ((Number) response.getOrDefault("matchedCount", 0)).intValue(),
                        ((Number) response.getOrDefault("mismatchCount", 0)).intValue(),
                        ((Number) response.getOrDefault("pendingCount", 0)).intValue(),
                        (String) response.getOrDefault("status", "UNKNOWN"),
                        (String) response.getOrDefault("summary", "No data available")
                );
            }
        } catch (Exception e) {
            log.error("Failed to fetch reconciliation status: {}", e.getMessage());
        }

        return new ReconciliationStatus(
                "RECON-" + LocalDate.now(), Instant.now(),
                0, 0, 0, "MCP_UNAVAILABLE",
                "Unable to retrieve reconciliation status. MCP server may be down.");
    }

    private InternalOpsResponse handleComplianceQuery(InternalOpsRequest request) {
        // Tier 2 - LLM-powered compliance query
        try {
            String complianceData = fetchComplianceData(request);

            String response = llmRouter.chat(SYSTEM_PROMPT, String.format("""
                            Employee %s asks: %s

                            Here is the current compliance data from the CRM system:
                            %s

                            Please provide a comprehensive compliance summary addressing the employee's query.
                            Include specific numbers, deadlines, and any regulatory concerns.
                            """, request.employeeId(), request.message(), complianceData));

            return new InternalOpsResponse(request.sessionId(), response, "COMPLIANCE_QUERY", false, null);

        } catch (Exception e) {
            log.error("Compliance query failed: {}", e.getMessage());
            return new InternalOpsResponse(request.sessionId(),
                    "Unable to process compliance query. Please contact the Compliance team directly.",
                    "COMPLIANCE_QUERY", false, null);
        }
    }

    private String fetchComplianceData(InternalOpsRequest request) {
        try {
            String url = crmUrl + "/api/v1/crm/compliance/pending";
            Map<String, Object> body = new HashMap<>();
            if (request.parameters() != null) {
                body.putAll(request.parameters());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response.toString() : "No compliance data available";
        } catch (Exception e) {
            log.warn("Failed to fetch compliance data from CRM: {}", e.getMessage());
            return "CRM system unavailable. Using cached data if available.";
        }
    }

    public InternalOpsResponse handleItHelpdesk(String employeeId, String query) {
        // Tier 1 - Static FAQ lookup (RAG in future scope)
        String answer = lookupItFaq(query);

        if (answer != null) {
            return new InternalOpsResponse(null, answer, "IT_HELPDESK", false, null);
        }

        // Fall back to LLM for unknown queries
        try {
            String response = llmRouter.chat(SYSTEM_PROMPT, String.format("""
                            Employee %s has an IT support question: %s

                            Provide helpful guidance based on standard IDFC First Bank IT procedures.
                            If you don't know the specific answer, suggest contacting IT support at ext. 4357.
                            """, employeeId, query));

            return new InternalOpsResponse(null, response, "IT_HELPDESK", false, null);
        } catch (Exception e) {
            log.error("IT helpdesk LLM call failed: {}", e.getMessage());
            return new InternalOpsResponse(null,
                    "Please contact IT support at extension 4357 or raise a ticket at the IT service desk portal.",
                    "IT_HELPDESK", false, null);
        }
    }

    private String lookupItFaq(String query) {
        if (query == null) return null;
        String lowerQuery = query.toLowerCase();

        Map<String, String> faqMap = Map.of(
                "finacle password", """
                        To reset your Finacle password:
                        1. Open the Finacle login page
                        2. Click "Forgot Password"
                        3. Enter your Employee ID
                        4. An OTP will be sent to your registered mobile number
                        5. Enter the OTP and set a new password
                        6. Password must be 8+ characters with uppercase, lowercase, number, and special character
                        Note: If your account is locked, contact IT support at ext. 4357.""",

                "vpn", """
                        To connect to the bank VPN:
                        1. Open the FortiClient VPN application
                        2. Select "IDFC First Bank - Corporate VPN"
                        3. Enter your AD credentials (Employee ID and password)
                        4. Approve the MFA notification on your registered device
                        5. Wait for the connection to establish
                        If VPN is not installed, download it from the IT Self-Service Portal.""",

                "email", """
                        For email-related issues:
                        - Outlook not syncing: Close and reopen Outlook, or clear the cache (File > Account Settings > clear offline items)
                        - Password reset: Use the AD password reset portal at password.idfcfirstbank.com
                        - Distribution list access: Raise a request through the IT service desk portal
                        - Email on mobile: Use the Microsoft Outlook app with your AD credentials""",

                "printer", """
                        To set up a network printer:
                        1. Go to Settings > Printers & Scanners > Add printer
                        2. Select your floor printer from the list (format: IDFC-FLOOR-XX-PRINTER)
                        3. If not listed, enter the printer IP address (check with your floor admin)
                        4. Install the required driver when prompted
                        For printer jams or hardware issues, raise a ticket with Facilities at ext. 2100.""",

                "leave", """
                        For leave-related queries, please use the HRMS portal:
                        1. Login to hrms.idfcfirstbank.com
                        2. Navigate to Leave Management > Leave Balance
                        3. Your current leave balance will be displayed by type (CL, SL, PL, etc.)
                        For policy questions, contact HR at hr.support@idfcfirstbank.com"""
        );

        for (Map.Entry<String, String> entry : faqMap.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    public InternalOpsResponse handleHrQuery(String employeeId, String query) {
        // Tier 0 - Mock response (HR system integration is future scope)
        String response = """
                HR Self-Service Portal
                ----------------------
                For leave balance, attendance, payslips, and other HR queries,
                please visit the HRMS portal: hrms.idfcfirstbank.com

                Quick Links:
                - Leave Balance: HRMS > Leave Management > Leave Balance
                - Payslips: HRMS > Payroll > View Payslips
                - Tax Declaration: HRMS > Tax > Investment Declaration
                - Attendance: HRMS > Attendance > My Attendance

                For further assistance, contact HR support:
                Email: hr.support@idfcfirstbank.com
                Extension: 2200
                """;

        return new InternalOpsResponse(null, response, "HR_QUERY", false, null);
    }

    private InternalOpsResponse handleGeneralQuery(InternalOpsRequest request) {
        try {
            String response = llmRouter.chat(SYSTEM_PROMPT,
                    String.format("Employee %s asks: %s", request.employeeId(), request.message()));

            return new InternalOpsResponse(request.sessionId(), response, "GENERAL", false, null);
        } catch (Exception e) {
            log.error("General query processing failed: {}", e.getMessage());
            return new InternalOpsResponse(request.sessionId(),
                    "Unable to process your query. Please try again or contact the relevant department.",
                    "GENERAL", false, null);
        }
    }

    private void publishAudit(String employeeId, String action, String policyResult, long startTime) {
        try {
            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "internal-ops-agent",
                    System.getenv("HOSTNAME"),
                    employeeId,
                    action != null ? action : "UNKNOWN",
                    "internal-ops",
                    policyResult,
                    null,
                    null,
                    System.currentTimeMillis() - startTime
            );
            auditEventPublisher.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish audit event: {}", e.getMessage());
        }
    }
}
