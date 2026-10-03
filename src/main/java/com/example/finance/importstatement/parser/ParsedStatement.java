package com.example.finance.importstatement.parser;

import java.time.LocalDate;
import java.util.List;

public record ParsedStatement(
        String source,
        LocalDate statementPeriodStart,
        LocalDate statementPeriodEnd,
        List<RawTransaction> transactions,
        int totalRows,
        List<String> errors
) {
}
