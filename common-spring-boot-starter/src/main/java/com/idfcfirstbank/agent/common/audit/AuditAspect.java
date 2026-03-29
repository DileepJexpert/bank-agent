package com.idfcfirstbank.agent.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.common.kafka.AuditEventPublisher;
import com.idfcfirstbank.agent.common.model.AuditEvent;
import com.idfcfirstbank.agent.common.util.MaskingUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

/**
 * AOP aspect that intercepts methods annotated with {@link AuditableAction} and
 * publishes audit events via {@link AuditEventPublisher}.
 *
 * <p>The aspect extracts the agent identity from the Spring Security context
 * (falling back to the {@code X-Agent-Id} HTTP header), scans method arguments
 * for a {@code customerId} field, records execution timing, and publishes a
 * complete {@link AuditEvent} regardless of whether the method succeeds or fails.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private static final String AGENT_ID_HEADER = "X-Agent-Id";
    private static final String UNKNOWN = "UNKNOWN";

    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditableAction)")
    public Object around(ProceedingJoinPoint joinPoint, AuditableAction auditableAction) throws Throwable {
        String agentId = resolveAgentId();
        String customerId = extractCustomerId(joinPoint.getArgs());
        String maskedArgs = maskArguments(joinPoint.getArgs());
        Instant startTime = Instant.now();

        Object result = null;
        String policyResult = "ALLOW";
        String responsePayload = null;

        try {
            result = joinPoint.proceed();
            responsePayload = maskObject(result);
            return result;
        } catch (Throwable ex) {
            policyResult = "ERROR";
            responsePayload = MaskingUtils.maskAll(ex.getMessage());
            throw ex;
        } finally {
            long latencyMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    startTime,
                    agentId,
                    resolveInstanceId(),
                    customerId != null ? MaskingUtils.maskAll(customerId) : UNKNOWN,
                    auditableAction.actionType(),
                    auditableAction.resourceType(),
                    policyResult,
                    maskedArgs,
                    responsePayload,
                    latencyMs
            );

            try {
                auditEventPublisher.publish(event);
            } catch (Exception publishEx) {
                log.error("Failed to publish audit event for action [{}]: {}",
                        auditableAction.actionType(), publishEx.getMessage(), publishEx);
            }
        }
    }

    /**
     * Resolves the agent ID from Spring Security context, falling back to the
     * X-Agent-Id HTTP header, and finally to "UNKNOWN".
     */
    private String resolveAgentId() {
        // Try Spring Security context first
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null
                && !authentication.getName().equals("anonymousUser")) {
            return authentication.getName();
        }

        // Fall back to HTTP header
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String headerValue = request.getHeader(AGENT_ID_HEADER);
                if (headerValue != null && !headerValue.isBlank()) {
                    return headerValue;
                }
            }
        } catch (Exception ex) {
            log.debug("Unable to extract agent ID from request header: {}", ex.getMessage());
        }

        return UNKNOWN;
    }

    /**
     * Scans method arguments for an object containing a {@code customerId} field
     * and returns its value.
     */
    private String extractCustomerId(Object[] args) {
        if (args == null) return null;

        for (Object arg : args) {
            if (arg == null) continue;

            // Direct string parameter named customerId is handled via reflection on the arg object
            if (arg instanceof String) continue;

            try {
                // Check declared fields of the argument class and its superclasses
                Class<?> clazz = arg.getClass();
                while (clazz != null && clazz != Object.class) {
                    for (Field field : clazz.getDeclaredFields()) {
                        if ("customerId".equals(field.getName())) {
                            field.setAccessible(true);
                            Object value = field.get(arg);
                            if (value != null) {
                                return value.toString();
                            }
                        }
                    }
                    clazz = clazz.getSuperclass();
                }

                // Also check record components for Java records
                if (arg.getClass().isRecord()) {
                    for (var component : arg.getClass().getRecordComponents()) {
                        if ("customerId".equals(component.getName())) {
                            Object value = component.getAccessor().invoke(arg);
                            if (value != null) {
                                return value.toString();
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Unable to extract customerId from argument of type {}: {}",
                        arg.getClass().getSimpleName(), ex.getMessage());
            }
        }

        // Check method parameter names for a direct customerId string parameter
        // This is a fallback for simple method signatures like doSomething(String customerId)
        return null;
    }

    /**
     * Serializes and masks all method arguments for inclusion in the audit event.
     */
    private String maskArguments(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        try {
            String serialized = objectMapper.writeValueAsString(args);
            return MaskingUtils.maskAll(serialized);
        } catch (Exception ex) {
            log.debug("Unable to serialize method arguments for audit: {}", ex.getMessage());
            return "[masked]";
        }
    }

    /**
     * Serializes and masks a result object for inclusion in the audit event.
     */
    private String maskObject(Object obj) {
        if (obj == null) return null;
        try {
            String serialized = objectMapper.writeValueAsString(obj);
            return MaskingUtils.maskAll(serialized);
        } catch (Exception ex) {
            log.debug("Unable to serialize response for audit: {}", ex.getMessage());
            return "[masked]";
        }
    }

    /**
     * Resolves the runtime instance identifier from the HOSTNAME environment variable,
     * falling back to a generated UUID.
     */
    private String resolveInstanceId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname != null && !hostname.isBlank() ? hostname : "local-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
