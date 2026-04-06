package com.idfcfirstbank.agent.collections.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an overdue account queued for collection action.
 * Accounts are prioritised by days overdue and priority score for batch processing.
 */
@Entity
@Table(name = "collections_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionsQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "queue_id")
    private UUID queueId;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "product_type", nullable = false, length = 50)
    private String productType;

    @Column(name = "overdue_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal overdueAmount;

    @Column(name = "days_overdue", nullable = false)
    private int daysOverdue;

    @Column(name = "priority")
    @Builder.Default
    private int priority = 0;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
