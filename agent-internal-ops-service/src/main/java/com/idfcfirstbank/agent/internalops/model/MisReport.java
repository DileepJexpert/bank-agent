package com.idfcfirstbank.agent.internalops.model;

import java.time.LocalDate;
import java.util.Map;

public record MisReport(
        String reportId,
        String branchId,
        String branchName,
        LocalDate reportDate,
        int totalTransactions,
        double totalCredit,
        double totalDebit,
        Map<String, Integer> transactionsByType,
        String status
) {
}
