package com.idfcfirstbank.agent.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Global filter that logs all incoming requests and outgoing responses for observability.
 * <p>
 * Sensitive headers (Authorization, Cookie, Set-Cookie) are masked in log output.
 * Adds correlation ID tracking if not already present in the request.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String REQUEST_START_TIME = "requestStartTime";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant startTime = Instant.now();
        exchange.getAttributes().put(REQUEST_START_TIME, startTime);

        ServerHttpRequest request = exchange.getRequest();

        // Ensure correlation ID is present
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            request = request.mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();
            exchange = exchange.mutate().request(request).build();
        }

        final String finalCorrelationId = correlationId;

        logRequest(request, finalCorrelationId);

        final ServerWebExchange finalExchange = exchange;

        return chain.filter(finalExchange)
                .then(Mono.fromRunnable(() -> logResponse(finalExchange, finalCorrelationId, startTime)));
    }

    private void logRequest(ServerHttpRequest request, String correlationId) {
        if (log.isInfoEnabled()) {
            String maskedHeaders = maskSensitiveHeaders(request.getHeaders());
            log.info("Incoming request: correlationId={} method={} path={} remoteAddr={} headers={}",
                    correlationId,
                    request.getMethod(),
                    request.getPath().value(),
                    request.getRemoteAddress(),
                    maskedHeaders);
        }
    }

    private void logResponse(ServerWebExchange exchange, String correlationId, Instant startTime) {
        if (log.isInfoEnabled()) {
            ServerHttpResponse response = exchange.getResponse();
            Duration duration = Duration.between(startTime, Instant.now());

            log.info("Outgoing response: correlationId={} status={} durationMs={} path={}",
                    correlationId,
                    response.getStatusCode(),
                    duration.toMillis(),
                    exchange.getRequest().getPath().value());
        }

        if (log.isDebugEnabled()) {
            String maskedHeaders = maskSensitiveHeaders(exchange.getResponse().getHeaders());
            log.debug("Response headers: correlationId={} headers={}", correlationId, maskedHeaders);
        }
    }

    private String maskSensitiveHeaders(HttpHeaders headers) {
        var builder = new StringBuilder("{");
        boolean first = true;
        for (var entry : headers.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            String key = entry.getKey();
            if (SENSITIVE_HEADERS.contains(key.toLowerCase())) {
                builder.append(key).append("=[******]");
            } else {
                builder.append(key).append("=").append(entry.getValue());
            }
        }
        builder.append("}");
        return builder.toString();
    }

    /**
     * Run before other filters to ensure correlation ID and timing are set early.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
