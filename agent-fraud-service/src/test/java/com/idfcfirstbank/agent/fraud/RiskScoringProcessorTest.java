package com.idfcfirstbank.agent.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfcfirstbank.agent.fraud.entity.CustomerProfile;
import com.idfcfirstbank.agent.fraud.model.FraudAlert;
import com.idfcfirstbank.agent.fraud.model.TransactionEvent;
import com.idfcfirstbank.agent.fraud.service.CustomerProfileService;
import com.idfcfirstbank.agent.fraud.stream.RiskScoringProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskScoringProcessorTest {

    @Mock
    private CustomerProfileService customerProfileService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RiskScoringProcessor riskScoringProcessor;

    private CustomerProfile defaultProfile;

    @BeforeEach
    void setUp() {
        defaultProfile = CustomerProfile.builder()
                .customerId("CUST001")
                .avgAmount(5000.0)
                .maxAmount(20000.0)
                .avgDailyCount(3.0)
                .commonDevices("[\"device-001\", \"device-002\"]")
                .commonMerchants("[\"merchant-001\", \"merchant-002\"]")
                .commonLocations("[\"19.0760,72.8777\", \"28.6139,77.2090\"]")
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("ML Score Tests")
    class MlScoreTests {

        @Test
        @DisplayName("Should add 0.15 for high amount transactions over 50000")
        void highAmountTransaction() {
            TransactionEvent txn = createTransaction(60000, "NEFT", "device-001", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreMl(txn, defaultProfile);

            assertThat(score).isGreaterThanOrEqualTo(0.15);
        }

        @Test
        @DisplayName("Should add 0.1 for UPI transactions over 25000")
        void upiHighAmountTransaction() {
            TransactionEvent txn = createTransaction(30000, "UPI", "device-001", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreMl(txn, defaultProfile);

            assertThat(score).isGreaterThanOrEqualTo(0.1);
        }

        @Test
        @DisplayName("Should add 0.2 for new device not in customer profile")
        void newDeviceTransaction() {
            TransactionEvent txn = createTransaction(1000, "CARD", "unknown-device", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreMl(txn, defaultProfile);

            assertThat(score).isGreaterThanOrEqualTo(0.2);
        }

        @Test
        @DisplayName("Should not add device score for known device")
        void knownDeviceTransaction() {
            TransactionEvent txn = createTransaction(1000, "CARD", "device-001", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreMl(txn, defaultProfile);

            assertThat(score).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should cap ML score at 1.0")
        void mlScoreCappedAtOne() {
            // High amount UPI with unknown device: 0.15 + 0.1 + 0.2 = 0.45
            TransactionEvent txn = createTransaction(60000, "UPI", "unknown-device", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreMl(txn, defaultProfile);

            assertThat(score).isLessThanOrEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Rules Score Tests")
    class RulesScoreTests {

        @Test
        @DisplayName("Should add 0.15 for transactions between midnight and 5 AM")
        void lateNightTransaction() {
            Instant lateNight = LocalDateTime.of(2024, 1, 15, 2, 30)
                    .toInstant(ZoneOffset.UTC);
            TransactionEvent txn = createTransaction(1000, "CARD", "device-001", "19.0760,72.8777",
                    lateNight);

            double score = riskScoringProcessor.scoreRules(txn);

            assertThat(score).isGreaterThanOrEqualTo(0.15);
        }

        @Test
        @DisplayName("Should not add time penalty for daytime transactions")
        void daytimeTransaction() {
            Instant daytime = LocalDateTime.of(2024, 1, 15, 14, 30)
                    .toInstant(ZoneOffset.UTC);
            TransactionEvent txn = createTransaction(1000, "CARD", "device-001", "19.0760,72.8777",
                    daytime);

            double score = riskScoringProcessor.scoreRules(txn);

            // No time penalty, no high-risk category, known location
            assertThat(score).isLessThan(0.15);
        }

        @Test
        @DisplayName("Should add 0.2 for high-risk merchant categories")
        void highRiskMerchantCategory() {
            TransactionEvent txn = new TransactionEvent(
                    "TXN001", "CUST001", 1000, "INR", "merchant-001",
                    "gambling", "CARD", "device-001", "192.168.1.1",
                    "19.0760,72.8777", Instant.now()
            );

            double score = riskScoringProcessor.scoreRules(txn);

            assertThat(score).isGreaterThanOrEqualTo(0.2);
        }

        @Test
        @DisplayName("Should add 0.25 for unknown location")
        void unknownLocationTransaction() {
            when(customerProfileService.getProfile("CUST001")).thenReturn(Optional.of(defaultProfile));

            TransactionEvent txn = createTransaction(1000, "CARD", "device-001", "51.5074,-0.1278",
                    Instant.now());

            double score = riskScoringProcessor.scoreRules(txn);

            assertThat(score).isGreaterThanOrEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("Behavioral Score Tests")
    class BehavioralScoreTests {

        @Test
        @DisplayName("Should add 0.2 when amount exceeds 2x customer average")
        void amountDeviation() {
            TransactionEvent txn = createTransaction(15000, "CARD", "device-001", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreBehavioral(txn, defaultProfile);

            assertThat(score).isGreaterThanOrEqualTo(0.2);
        }

        @Test
        @DisplayName("Should return 0.1 for first-time customer with no profile")
        void firstTimeCustomer() {
            TransactionEvent txn = createTransaction(1000, "CARD", "device-001", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreBehavioral(txn, null);

            assertThat(score).isEqualTo(0.1);
        }

        @Test
        @DisplayName("Should return 0 for normal amount within customer average")
        void normalAmountTransaction() {
            TransactionEvent txn = createTransaction(3000, "CARD", "device-001", "19.0760,72.8777",
                    Instant.now());

            double score = riskScoringProcessor.scoreBehavioral(txn, defaultProfile);

            assertThat(score).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Composite Risk Tests")
    class CompositeRiskTests {

        @Test
        @DisplayName("High amount UPI at 2AM from unknown device should result in HIGH or CRITICAL risk")
        void criticalRiskScenario() {
            when(customerProfileService.getProfile("CUST001")).thenReturn(Optional.of(defaultProfile));

            Instant lateNight = LocalDateTime.of(2024, 1, 15, 2, 0)
                    .toInstant(ZoneOffset.UTC);

            TransactionEvent txn = new TransactionEvent(
                    "TXN-CRITICAL", "CUST001", 60000, "INR", "merchant-003",
                    "gambling", "UPI", "unknown-device", "10.0.0.1",
                    "51.5074,-0.1278", lateNight
            );

            FraudAlert alert = riskScoringProcessor.computeRisk(txn);

            assertThat(alert.riskScore()).isGreaterThan(0.5);
            assertThat(alert.riskLevel()).isIn("HIGH", "CRITICAL");
            assertThat(alert.txnId()).isEqualTo("TXN-CRITICAL");
            assertThat(alert.customerId()).isEqualTo("CUST001");
            assertThat(alert.mlScore()).isGreaterThan(0);
            assertThat(alert.ruleScore()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Normal daytime transaction from known device should result in LOW risk")
        void lowRiskScenario() {
            when(customerProfileService.getProfile("CUST001")).thenReturn(Optional.of(defaultProfile));

            Instant daytime = LocalDateTime.of(2024, 1, 15, 14, 0)
                    .toInstant(ZoneOffset.UTC);

            TransactionEvent txn = new TransactionEvent(
                    "TXN-LOW", "CUST001", 2000, "INR", "merchant-001",
                    "retail", "CARD", "device-001", "192.168.1.1",
                    "19.0760,72.8777", daytime
            );

            FraudAlert alert = riskScoringProcessor.computeRisk(txn);

            assertThat(alert.riskScore()).isLessThan(0.4);
            assertThat(alert.riskLevel()).isEqualTo("LOW");
            assertThat(alert.txnId()).isEqualTo("TXN-LOW");
        }

        @Test
        @DisplayName("Composite score should be weighted: ML(40%) + Rules(30%) + Behavioral(30%)")
        void weightedScoreCalculation() {
            when(customerProfileService.getProfile("CUST001")).thenReturn(Optional.of(defaultProfile));

            TransactionEvent txn = createTransaction(3000, "CARD", "device-001", "19.0760,72.8777",
                    Instant.now());

            FraudAlert alert = riskScoringProcessor.computeRisk(txn);

            // Verify the composite score is the weighted sum
            double expectedComposite = (0.4 * alert.mlScore()) + (0.3 * alert.ruleScore()) + (0.3 * alert.behavioralScore());
            assertThat(alert.riskScore()).isCloseTo(expectedComposite, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("FraudAlert should have all required fields populated")
        void alertFieldsPopulated() {
            when(customerProfileService.getProfile("CUST001")).thenReturn(Optional.of(defaultProfile));

            TransactionEvent txn = createTransaction(1000, "CARD", "device-001", "19.0760,72.8777",
                    Instant.now());

            FraudAlert alert = riskScoringProcessor.computeRisk(txn);

            assertThat(alert.alertId()).isNotNull().isNotBlank();
            assertThat(alert.txnId()).isEqualTo("TXN001");
            assertThat(alert.customerId()).isEqualTo("CUST001");
            assertThat(alert.riskLevel()).isNotNull();
            assertThat(alert.actionTaken()).isNotNull();
            assertThat(alert.timestamp()).isNotNull();
        }
    }

    private TransactionEvent createTransaction(double amount, String channel, String deviceId,
                                                String geoLocation, Instant timestamp) {
        return new TransactionEvent(
                "TXN001", "CUST001", amount, "INR", "merchant-001",
                "retail", channel, deviceId, "192.168.1.1",
                geoLocation, timestamp
        );
    }
}
