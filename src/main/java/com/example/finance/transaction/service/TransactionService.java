package com.example.finance.transaction.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.finance.categorization.model.CategorizationRule;
import com.example.finance.categorization.repository.RuleRepository;
import com.example.finance.categorization.service.CategorizationService;
import com.example.finance.category.repository.CategoryRepository;
import com.example.finance.transaction.model.Transaction;
import com.example.finance.transaction.repository.TransactionRepository;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationService categorizationService;
    private final RuleRepository ruleRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            CategorizationService categorizationService,
            RuleRepository ruleRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationService = categorizationService;
        this.ruleRepository = ruleRepository;
    }

    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    public Transaction findById(String id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    public Transaction overrideCategory(String id, String categoryId) {
        Transaction transaction = findById(id);
        var category = categoryRepository.findById(categoryId)
                .filter(existing -> existing.active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active category not found"));

        Transaction.CategoryInfo info = new Transaction.CategoryInfo();
        info.categoryId = category.id;
        info.categoryName = category.name;
        info.source = Transaction.CategorySource.MANUAL;
        info.confidence = 1;
        info.manuallyOverridden = true;

        transaction.category = info;
        transaction.categorization.source = Transaction.CategorySource.MANUAL;
        transaction.categorization.confidence = 1;
        transaction.categorization.ruleId = null;
        transaction.categorization.categorizedAt = Instant.now();
        transaction.updatedAt = Instant.now();
        return transactionRepository.save(transaction);
    }

    public Transaction recategorize(String id) {
        Transaction transaction = findById(id);
        transaction.category = categorizationService.categorize(transaction);
        transaction.categorization.source = transaction.category.source;
        transaction.categorization.ruleId = transaction.category.ruleId;
        transaction.categorization.confidence = transaction.category.confidence;
        transaction.categorization.categorizedAt = Instant.now();
        transaction.updatedAt = Instant.now();
        return transactionRepository.save(transaction);
    }

    public BulkRecategorizeResult recategorizeUncategorized() {
        List<Transaction> transactions = transactionRepository.findUncategorized();
        List<CategorizationRule> rules = ruleRepository.findByActiveTrueOrderByPriorityAsc();
        Instant categorizedAt = Instant.now();
        int assignedByRule = 0;
        int assignedByMerchant = 0;
        int leftAtDefault = 0;

        for (Transaction transaction : transactions) {
            transaction.category = categorizationService.categorize(transaction, rules);
            transaction.categorization.source = transaction.category.source;
            transaction.categorization.ruleId = transaction.category.ruleId;
            transaction.categorization.confidence = transaction.category.confidence;
            transaction.categorization.categorizedAt = categorizedAt;
            transaction.updatedAt = categorizedAt;

            if (transaction.category.source == Transaction.CategorySource.RULE) {
                assignedByRule++;
            } else if (transaction.category.source == Transaction.CategorySource.MERCHANT) {
                assignedByMerchant++;
            } else {
                leftAtDefault++;
            }
        }

        if (!transactions.isEmpty()) {
            transactionRepository.saveAll(transactions);
        }
        return new BulkRecategorizeResult(transactions.size(), assignedByRule, assignedByMerchant, leftAtDefault);
    }

    public record BulkRecategorizeResult(int scanned, int assignedByRule, int assignedByMerchant, int leftAtDefault) {
    }
}
