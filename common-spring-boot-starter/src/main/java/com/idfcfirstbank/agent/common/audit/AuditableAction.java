package com.idfcfirstbank.agent.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit event publishing.
 * When applied, the {@link AuditAspect} will intercept the method call,
 * capture timing and context, and publish an {@link com.idfcfirstbank.agent.common.model.AuditEvent}
 * via Kafka.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditableAction {

    /**
     * The type of action being performed (e.g. "BALANCE_INQUIRY", "FUND_TRANSFER").
     */
    String actionType();

    /**
     * The type of resource being acted upon (e.g. "ACCOUNT", "LOAN", "CARD").
     * Defaults to empty string if not specified.
     */
    String resourceType() default "";
}
