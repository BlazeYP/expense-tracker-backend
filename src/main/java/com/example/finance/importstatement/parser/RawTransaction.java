package com.example.finance.importstatement.parser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.example.finance.transaction.model.Transaction;

public record RawTransaction(
        Instant transactionDateTime,
        Transaction.Type type,
        BigDecimal amount,
        String description,
        String counterpartyName,
        String upiTransactionId,
        String upiId,
        String bankName,
        String last4,
        Map<String, String> originalRowData
) {
}
