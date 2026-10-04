package com.example.finance.analytics.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.finance.transaction.model.Transaction;
import com.example.finance.transaction.repository.TransactionRepository;
import com.example.finance.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final TransactionRepository transactionRepository;

    public AnalyticsController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @Value("${app.timezone:Asia/Kolkata}") String timezone
    ) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }

        ZoneId zoneId = ZoneId.of(timezone);
        List<Transaction> transactions = transactionRepository.findByUserIdAndTransactionDateTimeBetween(CurrentUser.id(),
                from.atStartOfDay(zoneId).toInstant(),
                to.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1)
        );

        Map<String, BigDecimal> amountByCategory = new TreeMap<>();
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            if (transaction.type == Transaction.Type.DEBIT) {
                debitTotal = debitTotal.add(transaction.amount);
            } else {
                creditTotal = creditTotal.add(transaction.amount);
            }
            String categoryName = transaction.category == null || transaction.category.categoryName == null
                    ? "Uncategorized"
                    : transaction.category.categoryName;
            amountByCategory.merge(categoryName, transaction.amount, BigDecimal::add);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("transactionCount", transactions.size());
        result.put("debits", debitTotal);
        result.put("credits", creditTotal);
        result.put("net", creditTotal.subtract(debitTotal));
        result.put("amountByCategory", amountByCategory);
        return result;
    }

    @GetMapping("/monthly")
    public Map<String, Object> monthly(
            @RequestParam int year,
            @Value("${app.timezone:Asia/Kolkata}") String timezone
    ) {
        if (year < 1 || year > 9998) {
            throw new IllegalArgumentException("year must be between 1 and 9998");
        }

        ZoneId zoneId = ZoneId.of(timezone);
        Instant start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant();
        Instant end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant();
        List<Transaction> transactions = transactionRepository.findByUserIdAndTransactionDateTimeBetween(
                CurrentUser.id(), start, end.minusNanos(1));

        Map<Month, MonthlyTotals> totalsByMonth = new java.util.EnumMap<>(Month.class);
        for (Month month : Month.values()) {
            totalsByMonth.put(month, new MonthlyTotals());
        }
        for (Transaction transaction : transactions) {
            Month month = transaction.transactionDateTime.atZone(zoneId).getMonth();
            MonthlyTotals totals = totalsByMonth.get(month);
            if (transaction.type == Transaction.Type.DEBIT) {
                totals.totalDebits = totals.totalDebits.add(transaction.amount);
                totals.totalTransactionAmount = totals.totalTransactionAmount.subtract(transaction.amount);
            } else if (transaction.type == Transaction.Type.CREDIT) {
                totals.totalCredits = totals.totalCredits.add(transaction.amount);
                totals.totalTransactionAmount = totals.totalTransactionAmount.add(transaction.amount);
            }
        }

        List<Map<String, Object>> months = new ArrayList<>();
        for (Month month : Month.values()) {
            MonthlyTotals totals = totalsByMonth.get(month);
            Map<String, Object> monthResult = new LinkedHashMap<>();
            monthResult.put("month", month.getValue());
            monthResult.put("monthName", month.name());
            monthResult.put("totalTransactionAmount", totals.totalTransactionAmount);
            monthResult.put("totalDebits", totals.totalDebits);
            monthResult.put("totalCredits", totals.totalCredits);
            months.add(monthResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("months", months);
        return result;
    }

    private static class MonthlyTotals {
        private BigDecimal totalTransactionAmount = BigDecimal.ZERO;
        private BigDecimal totalDebits = BigDecimal.ZERO;
        private BigDecimal totalCredits = BigDecimal.ZERO;
    }
}
