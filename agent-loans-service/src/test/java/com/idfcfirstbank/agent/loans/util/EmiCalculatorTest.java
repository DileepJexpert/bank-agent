package com.idfcfirstbank.agent.loans.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EmiCalculator}.
 */
class EmiCalculatorTest {

    @Nested
    @DisplayName("calculateEMI")
    class CalculateEmiTests {

        @Test
        @DisplayName("should calculate correct EMI for standard home loan")
        void standardHomeLoan() {
            // 10L at 8.5% for 240 months (20 years)
            double emi = EmiCalculator.calculateEMI(1000000, 8.5, 240);
            // Known EMI for this combination is approximately 8678.23
            assertEquals(8678.23, emi, 1.0, "EMI for 10L at 8.5% for 20 years");
        }

        @Test
        @DisplayName("should calculate correct EMI for personal loan")
        void personalLoan() {
            // 5L at 12% for 36 months
            double emi = EmiCalculator.calculateEMI(500000, 12.0, 36);
            // Known EMI is approximately 16607.15
            assertEquals(16607.15, emi, 1.0, "EMI for 5L at 12% for 3 years");
        }

        @Test
        @DisplayName("should calculate correct EMI for car loan")
        void carLoan() {
            // 8L at 9% for 60 months
            double emi = EmiCalculator.calculateEMI(800000, 9.0, 60);
            // Known EMI is approximately 16607.55
            assertEquals(16607.55, emi, 1.0, "EMI for 8L at 9% for 5 years");
        }

        @Test
        @DisplayName("should handle zero interest rate")
        void zeroInterestRate() {
            double emi = EmiCalculator.calculateEMI(120000, 0, 12);
            assertEquals(10000.0, emi, 0.01, "EMI for zero interest should be principal/tenure");
        }

        @Test
        @DisplayName("should handle single month tenure")
        void singleMonthTenure() {
            double emi = EmiCalculator.calculateEMI(100000, 12.0, 1);
            // For 1 month: EMI = P + P*r = 100000 + 100000 * 0.01 = 101000
            assertEquals(101000.0, emi, 0.01);
        }

        @Test
        @DisplayName("should throw for negative principal")
        void negativePrincipal() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.calculateEMI(-100000, 8.5, 120));
        }

        @Test
        @DisplayName("should throw for zero principal")
        void zeroPrincipal() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.calculateEMI(0, 8.5, 120));
        }

        @Test
        @DisplayName("should throw for negative rate")
        void negativeRate() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.calculateEMI(100000, -1.0, 120));
        }

        @Test
        @DisplayName("should throw for zero tenure")
        void zeroTenure() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.calculateEMI(100000, 8.5, 0));
        }

        @Test
        @DisplayName("should throw for negative tenure")
        void negativeTenure() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.calculateEMI(100000, 8.5, -12));
        }

        @Test
        @DisplayName("total payment should exceed principal for positive rate")
        void totalPaymentExceedsPrincipal() {
            double principal = 500000;
            int tenure = 60;
            double emi = EmiCalculator.calculateEMI(principal, 10.0, tenure);
            double totalPayment = emi * tenure;
            assertTrue(totalPayment > principal,
                    "Total payment should exceed principal when interest rate > 0");
        }
    }

    @Nested
    @DisplayName("prepaymentImpact")
    class PrepaymentImpactTests {

        @Test
        @DisplayName("should calculate reduced EMI and tenure correctly")
        void standardPrepayment() {
            Map<String, Object> impact = EmiCalculator.prepaymentImpact(
                    1000000, 200000, 8.5, 120);

            assertNotNull(impact);
            assertTrue(impact.containsKey("originalEMI"));
            assertTrue(impact.containsKey("newEMI"));
            assertTrue(impact.containsKey("savedInterest"));
            assertTrue(impact.containsKey("newTenure"));
            assertTrue(impact.containsKey("tenureReduction"));
            assertTrue(impact.containsKey("savedInterestReducedTenure"));

            double originalEmi = (double) impact.get("originalEMI");
            double newEmi = (double) impact.get("newEMI");
            int newTenure = (int) impact.get("newTenure");
            int tenureReduction = (int) impact.get("tenureReduction");

            assertTrue(newEmi < originalEmi, "New EMI should be less than original");
            assertTrue(newTenure < 120, "New tenure should be less than original");
            assertTrue(tenureReduction > 0, "Tenure reduction should be positive");
        }

        @Test
        @DisplayName("should show positive interest savings")
        void positiveInterestSavings() {
            Map<String, Object> impact = EmiCalculator.prepaymentImpact(
                    500000, 100000, 10.0, 60);

            double savedInterest = (double) impact.get("savedInterest");
            double savedInterestTenure = (double) impact.get("savedInterestReducedTenure");

            assertTrue(savedInterest > 0, "Interest saved with reduced EMI should be positive");
            assertTrue(savedInterestTenure > 0, "Interest saved with reduced tenure should be positive");
        }

        @Test
        @DisplayName("should throw when prepayment exceeds outstanding")
        void prepaymentExceedsOutstanding() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.prepaymentImpact(100000, 200000, 8.5, 120));
        }

        @Test
        @DisplayName("should throw for zero prepayment amount")
        void zeroPrepayment() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.prepaymentImpact(100000, 0, 8.5, 120));
        }

        @Test
        @DisplayName("should throw for zero outstanding")
        void zeroOutstanding() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.prepaymentImpact(0, 100000, 8.5, 120));
        }

        @Test
        @DisplayName("should throw for zero remaining months")
        void zeroRemainingMonths() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.prepaymentImpact(100000, 50000, 8.5, 0));
        }

        @Test
        @DisplayName("should handle large prepayment close to outstanding")
        void largePrepayment() {
            Map<String, Object> impact = EmiCalculator.prepaymentImpact(
                    1000000, 950000, 8.5, 120);

            double newEmi = (double) impact.get("newEMI");
            int newTenure = (int) impact.get("newTenure");

            assertTrue(newEmi > 0, "New EMI should still be positive");
            assertTrue(newTenure > 0, "New tenure should still be positive");
            assertTrue(newTenure < 120, "New tenure should be much shorter");
        }
    }

    @Nested
    @DisplayName("generateAmortizationSchedule")
    class AmortizationScheduleTests {

        @Test
        @DisplayName("should generate correct number of entries")
        void correctEntryCount() {
            List<Map<String, Object>> schedule = EmiCalculator.generateAmortizationSchedule(
                    500000, 10.0, 12);
            assertEquals(12, schedule.size());
        }

        @Test
        @DisplayName("should have decreasing outstanding balance")
        void decreasingBalance() {
            List<Map<String, Object>> schedule = EmiCalculator.generateAmortizationSchedule(
                    500000, 10.0, 12);

            double prevBalance = 500000;
            for (Map<String, Object> row : schedule) {
                double balance = (double) row.get("outstandingBalance");
                assertTrue(balance < prevBalance, "Balance should decrease each month");
                prevBalance = balance;
            }
        }

        @Test
        @DisplayName("last entry should have zero outstanding balance")
        void lastEntryZeroBalance() {
            List<Map<String, Object>> schedule = EmiCalculator.generateAmortizationSchedule(
                    500000, 10.0, 24);

            Map<String, Object> lastRow = schedule.get(schedule.size() - 1);
            assertEquals(0.0, (double) lastRow.get("outstandingBalance"), 0.01);
        }

        @Test
        @DisplayName("should have increasing principal component over time")
        void increasingPrincipalComponent() {
            List<Map<String, Object>> schedule = EmiCalculator.generateAmortizationSchedule(
                    1000000, 8.5, 60);

            double firstPrincipal = (double) schedule.get(0).get("principalComponent");
            double lastPrincipal = (double) schedule.get(schedule.size() - 2).get("principalComponent");
            assertTrue(lastPrincipal > firstPrincipal,
                    "Principal component should increase over time in reducing balance method");
        }

        @Test
        @DisplayName("should have decreasing interest component over time")
        void decreasingInterestComponent() {
            List<Map<String, Object>> schedule = EmiCalculator.generateAmortizationSchedule(
                    1000000, 8.5, 60);

            double firstInterest = (double) schedule.get(0).get("interestComponent");
            double lastInterest = (double) schedule.get(schedule.size() - 2).get("interestComponent");
            assertTrue(lastInterest < firstInterest,
                    "Interest component should decrease over time");
        }

        @Test
        @DisplayName("each row should contain all required fields")
        void requiredFields() {
            List<Map<String, Object>> schedule = EmiCalculator.generateAmortizationSchedule(
                    100000, 12.0, 6);

            for (Map<String, Object> row : schedule) {
                assertTrue(row.containsKey("month"));
                assertTrue(row.containsKey("emi"));
                assertTrue(row.containsKey("principalComponent"));
                assertTrue(row.containsKey("interestComponent"));
                assertTrue(row.containsKey("outstandingBalance"));
            }
        }

        @Test
        @DisplayName("should throw for zero principal")
        void zeroPrincipal() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.generateAmortizationSchedule(0, 8.5, 12));
        }

        @Test
        @DisplayName("should throw for zero tenure")
        void zeroTenure() {
            assertThrows(IllegalArgumentException.class,
                    () -> EmiCalculator.generateAmortizationSchedule(100000, 8.5, 0));
        }

        @Test
        @DisplayName("month numbers should be sequential starting from 1")
        void sequentialMonthNumbers() {
            List<Map<String, Object>> schedule = EmiCalculator.generateAmortizationSchedule(
                    100000, 10.0, 12);

            for (int i = 0; i < schedule.size(); i++) {
                assertEquals(i + 1, schedule.get(i).get("month"));
            }
        }
    }
}
