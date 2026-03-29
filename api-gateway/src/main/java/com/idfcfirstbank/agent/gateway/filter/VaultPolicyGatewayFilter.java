package com.idfcfirstbank.agent.gateway.filter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Custom gateway filter that enforces vault policy evaluation for MCP server requests.
 * <p>
 * Before forwarding a request to any MCP server, this filter calls the vault-policy-service
 * to evaluate whether the requesting agent is authorized to perform the action. Based on the
 * policy decision:
 * <ul>
 *   <li>ALLOW - request proceeds to the downstream MCP server</li>
 *   <li>DENY - returns 403 Forbidden immediately</li>
 *   <li>ESCALATE - adds an escalation header and forwards the request</li>
 * </ul>
 */
@Slf4j
@Component
public class VaultPolicyGatewayFilter extends AbstractGatewayFilterFactory<VaultPolicyGatewayFilter.Config> {

    private static final String ESCALATION_HEADER = "X-Vault-Escalation-Required";
    private static final String POLICY_DECISION_HEADER = "X-Vault-Policy-Decision";

    private final WebClient webClient;

    @Value("${gateway.vault-policy.evaluation-url:lb://vault-policy-service/api/v1/vault/policy/evaluate}")
    private String policyEvaluationUrl;

    public VaultPolicyGatewayFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var request = exchange.getRequest();
            String agentId = request.getHeaders().getFirst("X-Agent-Id");
            String path = request.getPath().value();
            String method = request.getMethod().name();

            if (agentId == null || agentId.isBlank()) {
                log.warn("MCP request without X-Agent-Id header from {} to {}",
                        request.getRemoteAddress(), path);
                var response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                byte[] body = """
                        {"error":"unauthorized","message":"X-Agent-Id header is required for MCP server access"}
                        """.strip().getBytes(StandardCharsets.UTF_8);
                return response.writeWith(
                        Mono.just(response.bufferFactory().wrap(body)));
            }

            PolicyEvaluationRequest policyRequest = new PolicyEvaluationRequest(
                    agentId,
                    path,
                    method,
                    request.getHeaders().getFirst("X-Correlation-Id")
            );

            return webClient.post()
                    .uri(policyEvaluationUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(policyRequest)
                    .retrieve()
                    .bodyToMono(PolicyEvaluationResponse.class)
                    .flatMap(policyResponse -> {
                        String decision = policyResponse.decision();

                        log.info("Policy evaluation for agent={} path={} method={}: {}",
                                agentId, path, method, decision);

                        return switch (decision.toUpperCase()) {
                            case "ALLOW" -> {
                                var mutatedRequest = exchange.getRequest().mutate()
                                        .header(POLICY_DECISION_HEADER, "ALLOW")
                                        .build();
                                yield chain.filter(exchange.mutate().request(mutatedRequest).build());
                            }
                            case "DENY" -> {
                                log.warn("Policy DENY for agent={} on path={} reason={}",
                                        agentId, path, policyResponse.reason());
                                var response = exchange.getResponse();
                                response.setStatusCode(HttpStatus.FORBIDDEN);
                                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                String errorBody = """
                                        {"error":"policy_denied","message":"%s","agent":"%s","path":"%s"}
                                        """.formatted(
                                        policyResponse.reason() != null ? policyResponse.reason() : "Access denied by vault policy",
                                        agentId,
                                        path
                                ).strip();
                                yield response.writeWith(
                                        Mono.just(response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8))));
                            }
                            case "ESCALATE" -> {
                                log.info("Policy ESCALATE for agent={} on path={} reason={}",
                                        agentId, path, policyResponse.reason());
                                var mutatedRequest = exchange.getRequest().mutate()
                                        .header(ESCALATION_HEADER, "true")
                                        .header(POLICY_DECISION_HEADER, "ESCALATE")
                                        .header("X-Escalation-Reason",
                                                policyResponse.reason() != null ? policyResponse.reason() : "Policy requires escalation")
                                        .build();
                                yield chain.filter(exchange.mutate().request(mutatedRequest).build());
                            }
                            default -> {
                                log.error("Unknown policy decision '{}' for agent={} path={}. Defaulting to DENY.",
                                        decision, agentId, path);
                                var response = exchange.getResponse();
                                response.setStatusCode(HttpStatus.FORBIDDEN);
                                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                byte[] body = """
                                        {"error":"policy_error","message":"Unexpected policy evaluation result"}
                                        """.strip().getBytes(StandardCharsets.UTF_8);
                                yield response.writeWith(
                                        Mono.just(response.bufferFactory().wrap(body)));
                            }
                        };
                    })
                    .onErrorResume(ex -> {
                        log.error("Policy evaluation failed for agent={} path={}: {}",
                                agentId, path, ex.getMessage(), ex);
                        // Fail closed: deny access when policy service is unavailable
                        var response = exchange.getResponse();
                        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        byte[] body = """
                                {"error":"policy_service_unavailable","message":"Unable to evaluate policy. Access denied."}
                                """.strip().getBytes(StandardCharsets.UTF_8);
                        return response.writeWith(
                                Mono.just(response.bufferFactory().wrap(body)));
                    });
        };
    }

    @Getter
    @Setter
    public static class Config {
        // Configuration properties for the filter (extensible)
        private boolean failOpen = false;
    }

    private record PolicyEvaluationRequest(
            String agentId,
            String resourcePath,
            String httpMethod,
            String correlationId
    ) {}

    private record PolicyEvaluationResponse(
            String decision,
            String reason,
            String policyId
    ) {}
}
