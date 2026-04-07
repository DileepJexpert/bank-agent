package com.idfcfirstbank.agent.collections.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records each collections interaction (inbound or outbound) with a customer,
 * including call outcome, any offers made, and the conversation transcript.
 */
@Entity
@Table(name = "collections_interactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionsInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "interaction_id")
    private UUID interactionId;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "call_timestamp", nullable = false)
    private Instant callTimestamp;

    @Column(name = "duration")
    @Builder.Default
    private int duration = 0;

    @Column(name = "outcome", length = 50)
    private String outcome;

    @Column(name = "offer_made", length = 255)
    private String offerMade;

    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct;

    @Column(name = "promise_to_pay_date")
    private LocalDate promiseToPayDate;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
