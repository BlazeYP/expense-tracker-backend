package com.example.finance.categorization.service;

import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.finance.categorization.model.CategorizationRule;
import com.example.finance.categorization.repository.RuleRepository;
import com.example.finance.category.model.Category;
import com.example.finance.category.repository.CategoryRepository;
import com.example.finance.common.security.CurrentUser;
import com.example.finance.merchant.model.Merchant;
import com.example.finance.merchant.repository.MerchantRepository;
import com.example.finance.transaction.model.Transaction;

@Service
public class CategorizationService {

    private final CategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;
    private final RuleRepository ruleRepository;

    public CategorizationService(
            CategoryRepository categoryRepository,
            MerchantRepository merchantRepository,
            RuleRepository ruleRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.merchantRepository = merchantRepository;
        this.ruleRepository = ruleRepository;
    }

    public Transaction.CategoryInfo categorize(Transaction transaction) {
        return categorize(transaction, ruleRepository.findByUserIdAndActiveTrueOrderByPriorityAsc(CurrentUser.id()));
    }

    public Transaction.CategoryInfo categorize(Transaction transaction, List<CategorizationRule> rules) {
        if (transaction.category != null && transaction.category.manuallyOverridden) {
            return transaction.category;
        }

        String merchantName = normalize(transaction.counterparty == null ? "" : transaction.counterparty.originalName);
        if (!merchantName.isBlank()) {
            Optional<Merchant> match = merchantRepository.findByUserIdAndNormalizedNameAndActiveTrue(CurrentUser.id(), merchantName);
            if (match.isEmpty()) {
                match = merchantRepository.findByUserIdAndActiveTrue(CurrentUser.id()).stream()
                        .filter(merchant -> merchant.aliases != null
                                && merchant.aliases.stream()
                                        .map(CategorizationService::normalize)
                                        .anyMatch(merchantName::equals))
                        .findFirst();
            }
            if (match.isPresent() && match.get().defaultCategoryId != null) {
                Category category = categoryRepository.findByIdAndUserId(match.get().defaultCategoryId, CurrentUser.id()).orElse(null);
                if (category != null && category.active) {
                    return toCategoryInfo(category, Transaction.CategorySource.MERCHANT, null, 0.95);
                }
            }
        }

        for (CategorizationRule rule : rules) {
            if (rule.conditions == null || rule.conditions.isEmpty()) {
                continue;
            }
            if (rule.transactionType != null
                    && !rule.transactionType.isBlank()
                    && !rule.transactionType.equalsIgnoreCase(transaction.type.name())) {
                continue;
            }
            if (rule.conditions.stream().allMatch(condition -> matches(transaction, condition))) {
                Category category = categoryRepository.findByIdAndUserId(rule.categoryId, CurrentUser.id()).orElse(null);
                if (category != null && category.active) {
                    return toCategoryInfo(category, Transaction.CategorySource.RULE, rule.id, 0.9);
                }
            }
        }

        Category fallback = categoryRepository.findByUserIdAndNameIgnoreCase(CurrentUser.id(), "Other")
                .filter(category -> category.active)
                .orElseGet(() -> categoryRepository.findByUserIdAndActiveTrue(CurrentUser.id()).stream().findFirst().orElse(null));
        if (fallback == null) {
            return new Transaction.CategoryInfo();
        }
        return toCategoryInfo(fallback, Transaction.CategorySource.DEFAULT, null, 0.1);
    }

    private Transaction.CategoryInfo toCategoryInfo(
            Category category,
            Transaction.CategorySource source,
            String ruleId,
            double confidence
    ) {
        Transaction.CategoryInfo info = new Transaction.CategoryInfo();
        info.categoryId = category.id;
        info.categoryName = category.name;
        info.source = source;
        info.ruleId = ruleId;
        info.confidence = confidence;
        return info;
    }

    private boolean matches(Transaction transaction, CategorizationRule.Condition condition) {
        String value = switch (String.valueOf(condition.field).toUpperCase(Locale.ROOT)) {
            case "DESCRIPTION" -> transaction.description;
            case "COUNTERPARTY_NAME" -> transaction.counterparty.originalName;
            case "NORMALIZED_MERCHANT" -> transaction.counterparty.normalizedName;
            case "UPI_ID" -> transaction.counterparty.upiId;
            case "TRANSACTION_TYPE" -> transaction.type.name();
            case "AMOUNT" -> transaction.amount.toPlainString();
            default -> null;
        };
        if (value == null) {
            return false;
        }

        String actual = value.toLowerCase(Locale.ROOT);
        String expected = String.valueOf(condition.value).toLowerCase(Locale.ROOT);
        try {
            return switch (String.valueOf(condition.operator).toUpperCase(Locale.ROOT)) {
                case "EQUALS" -> actual.equals(expected);
                case "CONTAINS" -> actual.contains(expected);
                case "STARTS_WITH" -> actual.startsWith(expected);
                case "ENDS_WITH" -> actual.endsWith(expected);
                case "REGEX" -> Pattern.compile(condition.value, Pattern.CASE_INSENSITIVE).matcher(value).find();
                default -> false;
            };
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\b(limited|ltd|private|pvt|online)\\b", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
