package com.idfcfirstbank.agent.loans.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmiCalculatorTest {

    @Test
    void calculateEMI_standardHomeLoan() {
        // 50L at 8.5% for 20 years (240 months)
        double emi = EmiCalculator.calculateEMI(5000000, 8.5, 240);
        // Expected EMI ~43391
        assertTrue(emi > 43000 && emi < 44000, "EMI should be around 43391, got: " + emi);
    }

    @Test
    void calculateEMI_personalLoan() {
        // 5L at 12% for 3 years (36 months)
        double emi = EmiCalculator.calculateEMI(500000, 12.0, 36);
        // Expected EMI ~16607
        assertTrue(emi > 16500 && emi < 16700, "EMI should be around 16607, got: " + emi);
    }

    @Test
    void calculateEMI_zeroPrincipal() {
        double emi = EmiCalculator.calculateEMI(0, 8.5, 240);
        assertEquals(0.0, emi);
    }

    @Test
    void calculateEMI_zeroRate() {
        // At 0% interest, EMI = principal / tenure
        double emi = EmiCalculator.calculateEMI(120000, 0, 12);
        assertEquals(10000.0, emi, 0.01);
    }

    @Test
    void calculateEMI_onMonth() {
        double emi = EmiCalculator.calculateEMI(100000, 12.0, 1);
        // Should be slightly more than principal
        assertTrue(emi > 100000 && emi < 101100, "Single month EMI should be ~101000, got: " + emi);
    }

    @Test
    void prepaymentImpact_reducesEmiAndTenure() {
        Map<String, Object> impact = EmiCalculator.prepaymentImpact(5000000, 500000, 8.5, 180);

        assertNotNull(impact);
        assertTrue(impact.containsKey("newEMI"));
        assertTrue(impact.containsKey("savedInterest"));
        assertTrue(impact.containsKey("tenureReduction"));

        double newEMI = ((Number) impact.get("newEMI")).doubleValue();
        double savedInterest = ((Number) impact.get("savedInterest")).doubleValue();
        int tenureReduction = ((Number) impact.get("tenureReduction")).intValue();

        assertTrue(newEMI > 0, "New EMI should be positive");
        assertTrue(savedInterest > 0, "Should save some interest");
        assertTrue(tenureReduction > 0, "Should reduce tenure by at least 1 month");
    }

    @Test
    void prepaymentImpact_fullPrepayment() {
        Map<String, Object> impact = EmiCalculator.prepaymentImpact(500000, 500000, 8.5, 60);

        double newEMI = ((Number) impact.get("newEMI")).doubleValue();
        assertEquals(0.0, newEMI, 0.01, "Full prepayment should result in zero EMI");
    }

    @Test
    void generateAmortizationSchedule_correctEntries() {
        var schedule = EmiCalculator.generateAmortizationSchedule(100000, 12.0, 12);

        assertNotNull(schedule);
        assertEquals(12, schedule.size(), "Should have 12 monthly entries");

        // First entry should have principal + interest
        Map<String, Object> firstMonth = schedule.get(0);
        assertTrue(firstMonth.containsKey("month"));
        assertTrue(firstMonth.containsKey("emi"));
        assertTrue(firstMonth.containsKey("principal"));
        assertTrue(firstMonth.containsKey("interest"));
        assertTrue(firstMonth.containsKey("balance"));

        // Last month balance should be close to 0
        Map<String, Object> lastMonth = schedule.get(11);
        double finalBalance = ((Number) lastMonth.get("balance")).doubleValue();
        assertTrue(Math.abs(finalBalance) < 1.0, "Final balance should be near zero, got: " + finalBalance);
    }
}
