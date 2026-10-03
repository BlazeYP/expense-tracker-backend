package com.example.finance.categorization.ai;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/** Provider boundary for translating a batch into normalized categorization proposals. */
public interface AiCategorizationProvider {

    String name();

    default boolean supports(String configuredProvider) {
        return name().equalsIgnoreCase(configuredProvider);
    }

    Map<String, Proposal> categorize(List<TransactionInput> transactions, List<CategoryInput> categories);

    record TransactionInput(
            String reference,
            String type,
            BigDecimal amount,
            String currency,
            String description,
            String counterparty
    ) {}

    record CategoryInput(String id, String name, String description, String transactionType) {}

    record Proposal(String categoryId, double confidence, String reason) {}
}
