package com.idfcfirstbank.agent.fraud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.fraud.model.FraudAlert;
import com.idfcfirstbank.agent.fraud.model.RiskLevel;
import com.idfcfirstbank.agent.fraud.model.TransactionEvent;
import com.idfcfirstbank.agent.fraud.service.CustomerProfileService;
import com.idfcfirstbank.agent.fraud.stream.ResponseHandler;
import com.idfcfirstbank.agent.fraud.stream.RiskScoringProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class FraudStreamConfig {

    private final RiskScoringProcessor riskScoringProcessor;
    private final ResponseHandler responseHandler;
    private final CustomerProfileService customerProfileService;
    private final KafkaTemplate<String, FraudAlert> fraudAlertKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Primary listener: consumes transaction events for fraud detection.
     * Scores each transaction and triggers appropriate response actions.
     */
    @KafkaListener(
            topics = "txn-events",
            groupId = "fraud-detection-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processTransactionForFraud(ConsumerRecord<String, String> record) {
        try {
            TransactionEvent txn = objectMapper.readValue(record.value(), TransactionEvent.class);
            log.info("Received transaction event for fraud analysis: txnId={}, customerId={}",
                    txn.txnId(), txn.customerId());

            // Score the transaction
            FraudAlert alert = riskScoringProcessor.computeRisk(txn);

            // Publish fraud alert to Kafka
            fraudAlertKafkaTemplate.send("fraud-alerts", alert.txnId(), alert);
            log.info("Fraud alert published: alertId={}, riskLevel={}", alert.alertId(), alert.riskLevel());

            // Execute response actions based on risk level
            if (RiskLevel.valueOf(alert.riskLevel()) != RiskLevel.LOW) {
                responseHandler.handleAlert(alert);
            } else {
                // Even LOW alerts are saved to DB
                responseHandler.handleAlert(alert);
            }

        } catch (Exception e) {
            log.error("Error processing transaction event for fraud detection: key={}",
                    record.key(), e);
        }
    }

    /**
     * Profile update listener: consumes ALL transaction events to build customer behavioral baselines.
     * Uses a separate consumer group so it receives all events independently.
     */
    @KafkaListener(
            topics = "txn-events",
            groupId = "fraud-profile-update-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processTransactionForProfile(ConsumerRecord<String, String> record) {
        try {
            TransactionEvent txn = objectMapper.readValue(record.value(), TransactionEvent.class);
            log.debug("Updating customer profile from transaction: txnId={}, customerId={}",
                    txn.txnId(), txn.customerId());

            customerProfileService.updateProfileFromTransaction(txn);

        } catch (Exception e) {
            log.error("Error updating customer profile from transaction event: key={}",
                    record.key(), e);
        }
    }
}
