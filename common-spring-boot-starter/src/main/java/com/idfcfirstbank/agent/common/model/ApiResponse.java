package com.idfcfirstbank.agent.common.model;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic API response wrapper used across all agent platform services.
 * Provides a consistent response structure with tracing support.
 *
 * @param success   whether the request was successful
 * @param data      the response payload (null on error)
 * @param error     error message (null on success)
 * @param timestamp when the response was generated
 * @param traceId   distributed trace identifier from MDC or a generated UUID
 * @param <T>       type of the response payload
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        Instant timestamp,
        String traceId
) {

    /**
     * Creates a successful response wrapping the given data.
     *
     * @param data the response payload
     * @param <T>  type of the payload
     * @return an ApiResponse indicating success
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), resolveTraceId());
    }

    /**
     * Creates an error response with the given message.
     *
     * @param message the error description
     * @param <T>     type of the (absent) payload
     * @return an ApiResponse indicating failure
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now(), resolveTraceId());
    }

    /**
     * Resolves the trace ID from the SLF4J MDC context (commonly set by
     * Spring Cloud Sleuth / Micrometer Tracing). Falls back to a random UUID.
     */
    private static String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null && !traceId.isBlank() ? traceId : UUID.randomUUID().toString();
    }
}
