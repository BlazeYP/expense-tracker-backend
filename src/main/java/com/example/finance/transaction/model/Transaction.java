package com.example.finance.transaction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("transaction_records")
public class Transaction {

    @Id
    public String id;
    public String userId;
    public String source;
    public Instant transactionDateTime;
    public Type type;
    public BigDecimal amount;
    public String currency = "INR";
    public String description;
    public String upiTransactionId;
    public String importBatchId;
    public Counterparty counterparty = new Counterparty();
    public AccountInfo account = new AccountInfo();
    public CategoryInfo category = new CategoryInfo();
    public Categorization categorization = new Categorization();

    public String deduplicationHash;

    public Map<String, String> originalData = new LinkedHashMap<>();
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public Transaction() {
    }

    public enum Type {
        DEBIT,
        CREDIT
    }

    public enum CategorySource {
        RULE,
        MERCHANT,
        DEFAULT,
        MANUAL,
        AI
    }

    public static class Counterparty {
        public String originalName;
        public String normalizedName;
        public String upiId;
    }

    public static class AccountInfo {
        public String accountId;
        public String bankName;
        public String last4;
    }

    public static class CategoryInfo {
        public String categoryId;
        public String categoryName;
        public CategorySource source = CategorySource.DEFAULT;
        public String ruleId;
        public double confidence = 0.1;
        public boolean manuallyOverridden = false;
        public String reason;
    }

    public static class Categorization {
        public CategorySource source = CategorySource.DEFAULT;
        public String ruleId;
        public double confidence = 0.1;
        public Instant categorizedAt = Instant.now();
    }
}
