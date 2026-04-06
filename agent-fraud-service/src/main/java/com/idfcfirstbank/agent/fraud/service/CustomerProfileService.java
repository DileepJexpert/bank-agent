package com.idfcfirstbank.agent.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.fraud.entity.CustomerProfile;
import com.idfcfirstbank.agent.fraud.model.TransactionEvent;
import com.idfcfirstbank.agent.fraud.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerProfileService {

    private static final double EMA_ALPHA = 0.1;
    private static final int MAX_LIST_SIZE = 50;

    private final CustomerProfileRepository customerProfileRepository;
    private final ObjectMapper objectMapper;

    public Optional<CustomerProfile> getProfile(String customerId) {
        return customerProfileRepository.findById(customerId);
    }

    @Transactional
    public CustomerProfile updateProfileFromTransaction(TransactionEvent txn) {
        CustomerProfile profile = customerProfileRepository.findById(txn.customerId())
                .orElseGet(() -> CustomerProfile.builder()
                        .customerId(txn.customerId())
                        .avgAmount(0)
                        .maxAmount(0)
                        .avgDailyCount(0)
                        .commonDevices("[]")
                        .commonMerchants("[]")
                        .commonLocations("[]")
                        .updatedAt(Instant.now())
                        .build());

        // Update average amount using exponential moving average
        if (profile.getAvgAmount() == 0) {
            profile.setAvgAmount(txn.amount());
        } else {
            profile.setAvgAmount(
                    EMA_ALPHA * txn.amount() + (1 - EMA_ALPHA) * profile.getAvgAmount()
            );
        }

        // Update max amount
        if (txn.amount() > profile.getMaxAmount()) {
            profile.setMaxAmount(txn.amount());
        }

        // Update average daily count using EMA (increment by 1 per transaction)
        if (profile.getAvgDailyCount() == 0) {
            profile.setAvgDailyCount(1.0);
        } else {
            profile.setAvgDailyCount(
                    EMA_ALPHA * (profile.getAvgDailyCount() + 1) + (1 - EMA_ALPHA) * profile.getAvgDailyCount()
            );
        }

        // Update common devices
        if (txn.deviceId() != null) {
            profile.setCommonDevices(
                    addToJsonList(profile.getCommonDevices(), txn.deviceId())
            );
        }

        // Update common merchants
        if (txn.merchantId() != null) {
            profile.setCommonMerchants(
                    addToJsonList(profile.getCommonMerchants(), txn.merchantId())
            );
        }

        // Update common locations
        if (txn.geoLocation() != null) {
            profile.setCommonLocations(
                    addToJsonList(profile.getCommonLocations(), txn.geoLocation())
            );
        }

        profile.setUpdatedAt(Instant.now());

        return customerProfileRepository.save(profile);
    }

    private String addToJsonList(String jsonList, String value) {
        try {
            List<String> list = jsonList != null && !jsonList.isBlank()
                    ? new ArrayList<>(objectMapper.readValue(jsonList, new TypeReference<List<String>>() {}))
                    : new ArrayList<>();

            if (!list.contains(value)) {
                list.add(value);
                // Keep only the most recent entries
                if (list.size() > MAX_LIST_SIZE) {
                    list = list.subList(list.size() - MAX_LIST_SIZE, list.size());
                }
            }

            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Failed to update JSON list with value: {}", value, e);
            return jsonList;
        }
    }
}
