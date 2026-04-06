package com.idfcfirstbank.agent.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "customer_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {

    @Id
    @Column(name = "customer_id", length = 50)
    private String customerId;

    @Column(name = "avg_amount")
    private double avgAmount;

    @Column(name = "max_amount")
    private double maxAmount;

    @Column(name = "avg_daily_count")
    private double avgDailyCount;

    @Column(name = "common_devices", columnDefinition = "jsonb")
    private String commonDevices;

    @Column(name = "common_merchants", columnDefinition = "jsonb")
    private String commonMerchants;

    @Column(name = "common_locations", columnDefinition = "jsonb")
    private String commonLocations;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
